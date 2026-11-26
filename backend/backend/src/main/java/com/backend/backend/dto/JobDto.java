package com.backend.backend.dto;

import com.backend.backend.model.JobPosition;
import com.backend.backend.model.JobSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobDto {

    private JobDto() {
    }

    public record CreateRequest(
            @NotNull(message = "Choose the CV to adapt.") UUID cvId,
            @NotBlank(message = "Paste the job description first.")
            @Size(min = 20, max = 50_000, message = "The job description must be between 20 and 50,000 characters.") String text) {
    }

    public record Response(
            UUID id,
            UUID cvId,
            String rawText,
            JobSource source,
            String fileName,
            Instant createdAt,
            List<JobPosition> positions) {
    }
}
