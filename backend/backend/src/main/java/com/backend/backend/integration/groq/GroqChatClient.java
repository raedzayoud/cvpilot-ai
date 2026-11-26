package com.backend.backend.integration.groq;

import com.backend.backend.config.AppProperties;
import com.backend.backend.integration.ai.AiChatClient;
import com.backend.backend.integration.ai.AiException;
import com.backend.backend.integration.ai.AiException.Kind;
import com.backend.backend.integration.ai.AiRequest;
import com.backend.backend.integration.ai.AiToolRequest;
import com.backend.backend.integration.ai.AiToolTurn;
import com.backend.backend.integration.ai.AiUsageGuard;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Groq's OpenAI-compatible chat completions API. The API key is read from configuration
 * (GROQ_API_KEY) and only ever sent in the Authorization header to Groq; it is never logged.
 * Prompts and completions are not logged either, because they contain CV content.
 */
@Slf4j
@Component
public class GroqChatClient implements AiChatClient {

    /** Free-tier token-per-minute limits ask for waits of tens of seconds; honor them rather than failing. */
    private static final long MAX_RETRY_WAIT_MS = 30_000;

    private final AppProperties.Groq config;
    private final AiUsageGuard usageGuard;
    private final JsonMapper json;
    private final RestClient http;

    public GroqChatClient(AppProperties properties, AiUsageGuard usageGuard, JsonMapper json) {
        this.config = properties.ai().groq();
        this.usageGuard = usageGuard;
        this.json = json;

        HttpClient client = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(config.readTimeout());
        this.http = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return config.apiKey() != null && !config.apiKey().isBlank();
    }

    @Override
    public String providerName() {
        return "groq";
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public String complete(AiRequest request) {
        long started = System.nanoTime();
        String raw = post(json.writeValueAsString(payload(request)), request.purpose());
        return extractContent(raw, request.purpose(), started);
    }

    /** One step of a function-calling conversation: the model either answers or asks for tool calls. */
    @Override
    public AiToolTurn chat(AiToolRequest request) {
        long started = System.nanoTime();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model());
        payload.put("messages", request.messages());
        payload.put("tools", request.tools().stream().map(t -> Map.of("type", "function", "function", Map.of(
                "name", t.name(), "description", t.description(), "parameters", t.parameters()))).toList());
        payload.put("tool_choice", request.requireTool() ? "required" : "auto");
        payload.put("temperature", request.temperature());
        payload.put("max_completion_tokens", request.maxTokens());
        if (config.reasoningEffort() != null && !config.reasoningEffort().isBlank()) {
            payload.put("reasoning_effort", config.reasoningEffort().trim());
        }
        String raw = post(json.writeValueAsString(payload), request.purpose());
        try {
            JsonNode root = json.readTree(raw == null ? "" : raw);
            JsonNode message = root.path("choices").path(0).path("message");
            List<AiToolTurn.Call> calls = new ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                calls.add(new AiToolTurn.Call(call.path("id").asString(""), call.path("function").path("name").asString(""),
                        call.path("function").path("arguments").asString("{}")));
            }
            String content = message.path("content").isString() ? message.path("content").asString() : "";
            JsonNode usage = root.path("usage");
            log.info("Groq {} ok in {} ms (model={}, prompt_tokens={}, completion_tokens={}, tool_calls={})",
                    request.purpose(), elapsedMs(started), config.model(),
                    usage.path("prompt_tokens").asInt(-1), usage.path("completion_tokens").asInt(-1), calls.size());
            if (content.isBlank() && calls.isEmpty()) {
                throw new AiException(Kind.BAD_RESPONSE, "Groq response had neither content nor tool calls");
            }
            return new AiToolTurn(content, calls);
        } catch (JacksonException e) {
            throw new AiException(Kind.BAD_RESPONSE, "Groq response was not valid JSON", e);
        }
    }

    /** POSTs a chat completion with retries on 429/5xx; counts once against the daily AI budget. */
    private String post(String body, String purpose) {
        if (!isConfigured()) throw new AiException(Kind.NOT_CONFIGURED, "GROQ_API_KEY is not set");
        usageGuard.acquire();
        int attempt = 0;
        while (true) {
            long started = System.nanoTime();
            try {
                return http.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                log.warn("Groq {} failed: HTTP {} after {} ms (attempt {}): {}", purpose, status, elapsedMs(started), attempt + 1,
                        errorSummary(e.getResponseBodyAsString()));
                int allowedRetries = status == 429 ? Math.max(2, config.maxRetries()) : config.maxRetries();
                if (retryable && attempt < allowedRetries) {
                    sleep(retryDelayMs(e, attempt));
                    attempt++;
                    continue;
                }
                throw mapStatus(status, e);
            } catch (ResourceAccessException e) {
                boolean timeout = hasCause(e, HttpTimeoutException.class);
                log.warn("Groq {} {} after {} ms (attempt {})", purpose, timeout ? "timed out" : "unreachable",
                        elapsedMs(started), attempt + 1);
                if (!timeout && attempt < config.maxRetries()) {
                    sleep(500L * (attempt + 1));
                    attempt++;
                    continue;
                }
                throw new AiException(timeout ? Kind.TIMEOUT : Kind.UNAVAILABLE, "Groq request failed: " + e.getClass().getSimpleName(), e);
            }
        }
    }

    private Map<String, Object> payload(AiRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model());
        payload.put("messages", request.messages().stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList());
        payload.put("temperature", request.temperature());
        // Reasoning tokens count toward this cap on reasoning models.
        payload.put("max_completion_tokens", request.maxTokens());
        if (request.jsonMode()) payload.put("response_format", Map.of("type", "json_object"));
        if (config.reasoningEffort() != null && !config.reasoningEffort().isBlank()) {
            payload.put("reasoning_effort", config.reasoningEffort().trim());
        }
        return payload;
    }

    private String extractContent(String raw, String purpose, long started) {
        try {
            JsonNode root = json.readTree(raw == null ? "" : raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isString() || content.asString().isBlank()) {
                throw new AiException(Kind.BAD_RESPONSE, "Groq response had no message content");
            }
            JsonNode usage = root.path("usage");
            log.info("Groq {} ok in {} ms (model={}, prompt_tokens={}, completion_tokens={}, finish={})",
                    purpose, elapsedMs(started), config.model(),
                    usage.path("prompt_tokens").asInt(-1), usage.path("completion_tokens").asInt(-1),
                    root.path("choices").path(0).path("finish_reason").asString("?"));
            return content.asString();
        } catch (JacksonException e) {
            throw new AiException(Kind.BAD_RESPONSE, "Groq response was not valid JSON", e);
        }
    }

    /** Error code and a short message from Groq's error body; never the generated text (it may contain CV content). */
    private String errorSummary(String body) {
        try {
            JsonNode error = json.readTree(body == null || body.isBlank() ? "{}" : body).path("error");
            String message = error.path("message").asString("");
            if (message.length() > 160) message = message.substring(0, 160) + "...";
            return error.path("code").asString(error.path("type").asString("?")) + " - " + message;
        } catch (JacksonException e) {
            return "(unparseable error body)";
        }
    }

    private static AiException mapStatus(int status, RestClientResponseException e) {
        return switch (status) {
            case 401, 403 -> new AiException(Kind.UNAVAILABLE, "Groq rejected the API key (HTTP " + status + ")", e);
            case 429 -> new AiException(Kind.RATE_LIMITED, "Groq rate limit (HTTP 429)", e);
            case 400, 413, 422 -> new AiException(Kind.BAD_RESPONSE, "Groq rejected the request (HTTP " + status + ")", e);
            default -> new AiException(Kind.UNAVAILABLE, "Groq error (HTTP " + status + ")", e);
        };
    }

    private static long retryDelayMs(RestClientResponseException e, int attempt) {
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (header != null) {
            try {
                return Math.max(0, Math.min(MAX_RETRY_WAIT_MS, (long) (Double.parseDouble(header.trim()) * 1000)));
            } catch (NumberFormatException ignored) {
                // HTTP-date form: fall through to backoff
            }
        }
        return Math.min(MAX_RETRY_WAIT_MS, 1000L * (1L << attempt));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException(Kind.UNAVAILABLE, "Interrupted while waiting to retry", e);
        }
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }
}
