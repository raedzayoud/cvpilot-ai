package com.backend.backend.integration.ai;

import java.util.List;

/**
 * @param purpose short label used only for logs and metrics (e.g. "cv-extraction"); never the prompt
 * @param jsonMode ask the provider to return a single JSON object
 */
public record AiRequest(String purpose, List<Message> messages, boolean jsonMode, double temperature, int maxTokens) {

    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}
