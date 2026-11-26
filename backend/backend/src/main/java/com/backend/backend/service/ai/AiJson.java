package com.backend.backend.service.ai;

import com.backend.backend.integration.ai.AiException;
import com.backend.backend.integration.ai.AiException.Kind;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Defensive parsing of model output: tolerates code fences and leading prose, rejects anything else. */
@Component
public class AiJson {

    private final JsonMapper mapper;

    public AiJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode parseObject(String content) {
        String text = content == null ? "" : content.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw new AiException(Kind.BAD_RESPONSE, "AI output contained no JSON object");
        try {
            JsonNode node = mapper.readTree(text.substring(start, end + 1));
            if (!node.isObject()) throw new AiException(Kind.BAD_RESPONSE, "AI output was not a JSON object");
            return node;
        } catch (JacksonException e) {
            throw new AiException(Kind.BAD_RESPONSE, "AI output was malformed JSON", e);
        }
    }

    public <T> T convert(JsonNode node, Class<T> type) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new AiException(Kind.BAD_RESPONSE, "AI output had no " + type.getSimpleName());
        }
        try {
            T value = mapper.treeToValue(node, type);
            if (value == null) throw new AiException(Kind.BAD_RESPONSE, "AI output had no " + type.getSimpleName());
            return value;
        } catch (JacksonException | IllegalArgumentException e) {
            throw new AiException(Kind.BAD_RESPONSE, "AI output did not match " + type.getSimpleName(), e);
        }
    }

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    /** Truncates input sent to the model; long documents cost tokens without improving results. */
    public static String clip(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n[...truncated]";
    }
}
