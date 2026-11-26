package com.backend.backend.dto;

import com.backend.backend.model.CvProfile;
import com.backend.backend.model.CvSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class CvDto {

    private CvDto() {
    }

    public record Response(
            UUID id,
            String fileName,
            String mimeType,
            CvSource sourceFormat,
            long sizeBytes,
            String language,
            Instant updatedAt,
            String extractionNote,
            CvProfile profile) {
    }

    public record Summary(UUID id, String fileName, CvSource sourceFormat, long sizeBytes, String language, Instant updatedAt) {
    }

    /** LaTeX source pasted as text instead of uploaded as a file. */
    public record TextUpload(
            @Size(max = 200, message = "File name must be at most 200 characters.") String fileName,
            @NotBlank(message = "Paste the LaTeX source first.")
            @Size(max = 200_000, message = "LaTeX source must be at most 200,000 characters.") String source) {
    }
}
