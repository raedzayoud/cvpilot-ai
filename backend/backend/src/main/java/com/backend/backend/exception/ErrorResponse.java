package com.backend.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/** Every error body has a human-readable {@code message}; the frontend shows it as-is. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path, Map<String, String> fieldErrors) {
}
