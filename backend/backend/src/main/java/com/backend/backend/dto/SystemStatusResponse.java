package com.backend.backend.dto;

/** What the UI needs to show whether AI features are available. Never includes secrets. */
public record SystemStatusResponse(boolean aiConfigured, String aiProvider, String aiModel, int aiDailyRemaining) {
}
