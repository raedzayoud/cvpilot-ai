package com.backend.backend.dto;

import com.backend.backend.model.CvProfile;
import com.backend.backend.model.ExportFormat;
import com.backend.backend.model.agent.AgentFacts;
import com.backend.backend.model.agent.ProposalStatus;
import com.backend.backend.model.agent.ProposedChange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AgentDto {

    private AgentDto() {
    }

    public record CreateSession(@NotNull(message = "Choose the CV to work on.") UUID cvId) {
    }

    public record SendMessage(
            @NotBlank(message = "Type an instruction first.")
            @Size(max = 12_000, message = "Messages are limited to 12,000 characters (paste long job descriptions in parts).")
            String text) {
    }

    /**
     * Optional manual edit applied when accepting: {@code text} for headline/summary, {@code list} for
     * skills or experience bullets, {@code description} for projects.
     */
    public record AcceptRequest(
            @Size(max = 4000) String text,
            @Size(max = 100) List<@Size(max = 1500) String> list,
            @Size(max = 2000) String description) {
    }

    public record ExportRequest(@NotNull(message = "Choose an export format.") ExportFormat format) {
    }

    public record SessionSummary(UUID id, UUID cvId, String cvFileName, String title, Instant updatedAt, Instant savedAt) {
    }

    public record Message(String id, String role, String text, Instant at, AgentFacts.TurnMeta meta) {
    }

    public record Proposal(UUID id, ProposalStatus status, ProposedChange change, Instant createdAt, Instant decidedAt) {
    }

    public record Stats(long pending, long accepted, long rejected) {
    }

    /** Everything the workspace screen shows, returned after every action so the UI never goes stale. */
    public record Workspace(
            SessionSummary session,
            List<Message> messages,
            List<Proposal> proposals,
            CvProfile working,
            CvProfile original,
            List<String> sectionOrder,
            AgentFacts.JobTarget jobTarget,
            List<AgentFacts.ConfirmedFact> confirmedFacts,
            Stats stats,
            boolean unsavedChanges,
            boolean aiAvailable) {
    }
}
