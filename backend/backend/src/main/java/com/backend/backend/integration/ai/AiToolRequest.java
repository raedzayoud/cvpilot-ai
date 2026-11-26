package com.backend.backend.integration.ai;

import java.util.List;
import java.util.Map;

/**
 * A function-calling request in the OpenAI-compatible wire format that Groq (and most providers)
 * accept: messages are role/content maps, including assistant messages carrying "tool_calls" and
 * "tool" messages carrying results.
 */
public record AiToolRequest(
        String purpose,
        List<Map<String, Object>> messages,
        List<Tool> tools,
        double temperature,
        int maxTokens,
        /** Force the model to call at least one tool in this step (tool_choice "required"). */
        boolean requireTool) {

    /** @param parameters a JSON Schema object describing the arguments */
    public record Tool(String name, String description, Map<String, Object> parameters) {
    }
}
