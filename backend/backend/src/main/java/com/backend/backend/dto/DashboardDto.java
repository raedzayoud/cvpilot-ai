package com.backend.backend.dto;

import com.backend.backend.model.ActivityType;
import java.time.Instant;
import java.util.List;

public final class DashboardDto {

    private DashboardDto() {
    }

    /** {@code averageScore} and {@code bestScore} are null until the first analysis exists. */
    public record Stats(long cvCount, long analysisCount, long generatedCount, Integer averageScore, Integer bestScore) {
    }

    public record ActivityResponse(String id, ActivityType type, String label, Instant at) {
    }

    public record Response(
            Stats stats,
            List<JobDto.Response> recentJobs,
            List<AnalysisDto.Summary> recentAnalyses,
            List<ActivityResponse> activity) {
    }
}
