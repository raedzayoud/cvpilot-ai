package com.backend.backend.model.agent;

import com.backend.backend.model.JobRequirements;
import com.backend.backend.util.EnumValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.List;

/** Small value types shared by the agent: provenance of claims, confirmed facts, job target, turn metadata. */
public final class AgentFacts {

    private AgentFacts() {
    }

    /** Where a skill/keyword in a proposal is backed from. */
    public enum Source {
        CV, USER_CONFIRMED;

        @JsonValue
        public String value() {
            return EnumValues.toJson(this);
        }

        @JsonCreator
        public static Source from(String value) {
            return EnumValues.parse(Source.class, value);
        }
    }

    public record Evidence(String term, Source source) {
    }

    /**
     * Something the user said about themselves. {@code quote} is verified to be an exact excerpt of one
     * of the user's own messages, so the model cannot "confirm" facts on the user's behalf.
     */
    public record ConfirmedFact(String quote, List<String> skills, Instant at) {
    }

    /** The job the CV is being tailored to, plus how the current CV matches it. */
    public record JobTarget(String title, JobRequirements requirements, List<String> matchedSkills,
                            List<String> missingSkills, int score) {
    }

    /** What happened in one agent turn, shown in the UI for transparency. */
    public record TurnMeta(AgentIntent intent, List<String> toolsUsed, List<String> blockedClaims, List<String> proposalIds) {
    }
}
