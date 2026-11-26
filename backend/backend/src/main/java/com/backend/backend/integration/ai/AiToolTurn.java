package com.backend.backend.integration.ai;

import java.util.List;

/** The model's answer to one function-calling step: text, tool calls to execute, or both. */
public record AiToolTurn(String content, List<Call> toolCalls) {

    public record Call(String id, String name, String argumentsJson) {
    }

    public boolean wantsTools() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
