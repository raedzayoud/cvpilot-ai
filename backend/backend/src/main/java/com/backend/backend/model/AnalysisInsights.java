package com.backend.backend.model;

import java.util.List;

/** Match score, breakdown, gaps and learning suggestions; computed deterministically from CV + job. */
public record AnalysisInsights(
        String analysisId,
        int score,
        List<ScoreCriterion> criteria,
        List<String> strengths,
        List<Weakness> weaknesses,
        List<String> improvements,
        List<SkillRecommendation> skillRecommendations,
        String disclaimer) {

    public record CriterionEvidence(String requirement, MatchLevel level) {
    }

    public record ScoreCriterion(
            ScoreCriterionKey key, String label, int weight, double ratio, int points, List<CriterionEvidence> evidence) {
    }

    public record Weakness(
            String id, String title, String description, String whyItMatters,
            Priority severity, String improvement, WeaknessStatus status) {
    }

    public record SkillRecommendation(
            String id, String skill, String why, String relation, Priority priority, String activity) {
    }
}
