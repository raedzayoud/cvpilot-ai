package com.backend.backend.dto;

import com.backend.backend.model.AnalysisInsights;
import com.backend.backend.model.ChatRole;
import com.backend.backend.model.CvDecision;
import com.backend.backend.model.CvSource;
import com.backend.backend.model.DecisionAction;
import com.backend.backend.model.ExportFormat;
import com.backend.backend.model.SectionProposal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AnalysisDto {

    private AnalysisDto() {
    }

    public record CreateRequest(
            @NotNull(message = "cvId is required.") UUID cvId,
            @NotNull(message = "jobId is required.") UUID jobId) {
    }

    public record Response(
            UUID id,
            UUID cvId,
            UUID jobId,
            String jobTitle,
            String language,
            CvSource sourceFormat,
            int score,
            boolean exported,
            Instant createdAt) {
    }

    public record Summary(
            UUID id,
            String jobTitle,
            String cvFileName,
            CvSource sourceFormat,
            int score,
            boolean exported,
            Instant createdAt) {
    }

    public record CustomizationResponse(
            UUID analysisId, String language, List<SectionProposal> sections, List<CvDecision> decisions) {
    }

    public record ChatMessageResponse(String id, ChatRole role, String text, Instant at) {
    }

    public record ChatTurnResponse(
            List<ChatMessageResponse> messages, CustomizationResponse customization, AnalysisInsights insights) {
    }

    public record ChatRequest(
            @NotBlank(message = "Type a message first.")
            @Size(max = 2000, message = "Messages are limited to 2,000 characters.") String text) {
    }

    public record SectionRequest(
            @NotNull(message = "Section text is required.")
            @Size(max = 20_000, message = "Section text is limited to 20,000 characters.") String text) {
    }

    public record DecisionRequest(@NotNull(message = "Choose an action.") DecisionAction action) {
    }

    public record ExportRequest(@NotNull(message = "Choose an export format.") ExportFormat format) {
    }
}
