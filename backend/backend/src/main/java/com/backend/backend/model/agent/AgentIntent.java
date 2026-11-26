package com.backend.backend.model.agent;

import com.backend.backend.util.EnumValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** What the user is asking for; detected before the model runs and used to plan the turn. */
public enum AgentIntent {
    TAILOR_TO_JOB, OPTIMIZE_ATS, REWRITE, ADD_CONTENT, REMOVE_CONTENT, REORDER, QUESTION, OTHER;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static AgentIntent from(String value) {
        return EnumValues.parse(AgentIntent.class, value);
    }
}
