package com.backend.backend.integration.ai;

import lombok.Getter;

/** AI failure with a category for HTTP mapping and a message that is safe to show users. */
@Getter
public class AiException extends RuntimeException {

    public enum Kind { NOT_CONFIGURED, UNAVAILABLE, RATE_LIMITED, BUDGET_EXHAUSTED, TIMEOUT, BAD_RESPONSE }

    private final Kind kind;
    private final String userMessage;

    public AiException(Kind kind, String detail, Throwable cause) {
        super(detail, cause);
        this.kind = kind;
        this.userMessage = switch (kind) {
            case NOT_CONFIGURED -> "The AI assistant is not configured on the server (GROQ_API_KEY is missing).";
            case UNAVAILABLE -> "The AI service is unavailable right now. Try again in a moment.";
            case RATE_LIMITED -> "The AI service is busy. Wait a few seconds and try again.";
            case BUDGET_EXHAUSTED -> "Today's AI usage limit has been reached. Try again tomorrow.";
            case TIMEOUT -> "The AI service took too long to answer. Try again.";
            case BAD_RESPONSE -> "The AI returned an unexpected answer. Try again.";
        };
    }

    public AiException(Kind kind, String detail) {
        this(kind, detail, null);
    }
}
