package com.backend.backend.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Typed view of the {@code app.*} settings in application.properties (all overridable by env vars). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(@DefaultValue Cors cors, @DefaultValue Ai ai, @DefaultValue RateLimit rateLimit, @DefaultValue Limits limits,
                            @DefaultValue Latex latex) {

    /** PDF export compiles the LaTeX template; when disabled or failing, a built-in PDF layout is used. */
    public record Latex(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("tectonic") String compiler,
            @DefaultValue("120s") Duration timeout) {
    }

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }

    public record Ai(
            @DefaultValue("500") int dailyRequestLimit,
            @DefaultValue("24000") int maxInputChars,
            @DefaultValue Groq groq) {
    }

    public record Groq(
            String apiKey,
            @DefaultValue("openai/gpt-oss-120b") String model,
            /** Sent as reasoning_effort for reasoning models; set empty to omit for other models. */
            @DefaultValue("low") String reasoningEffort,
            @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("60s") Duration readTimeout,
            @DefaultValue("1") int maxRetries) {
    }

    /** Per-client sliding window for endpoints that call the AI provider. */
    public record RateLimit(@DefaultValue("20") int aiRequests, @DefaultValue("10m") Duration aiWindow) {
    }

    public record Limits(@DefaultValue("1048576") long maxJsonBodyBytes) {
    }
}
