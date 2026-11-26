package com.backend.backend.model;

public record JobPosition(String id, String title, String summary, JobRequirements requirements) {

    public JobPosition {
        id = Lists.text(id);
        title = title == null || title.isBlank() ? "Job posting" : title.trim();
        summary = Lists.text(summary);
        requirements = requirements == null ? JobRequirements.empty() : requirements;
    }
}
