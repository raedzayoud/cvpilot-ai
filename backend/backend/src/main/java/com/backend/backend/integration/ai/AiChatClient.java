package com.backend.backend.integration.ai;

/**
 * Provider-neutral chat completion. Business services depend on this interface only, so switching
 * from Groq to another provider means adding one implementation, not touching business logic.
 */
public interface AiChatClient {

    /** Returns the assistant message text. Throws {@link AiException} on any failure. */
    String complete(AiRequest request);

    /** One function-calling step. Providers without tool support keep the default. */
    default AiToolTurn chat(AiToolRequest request) {
        throw new AiException(AiException.Kind.UNAVAILABLE, providerName() + " does not support tool calling");
    }

    boolean isConfigured();

    String providerName();

    String model();
}
