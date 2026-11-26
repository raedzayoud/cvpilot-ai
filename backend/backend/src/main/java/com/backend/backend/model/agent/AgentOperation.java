package com.backend.backend.model.agent;

import com.backend.backend.util.EnumValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Every edit the agent can propose. Each maps to one tool and one validation rule set. */
public enum AgentOperation {
    UPDATE_HEADLINE, UPDATE_SUMMARY, UPDATE_SKILLS,
    ADD_EXPERIENCE, UPDATE_EXPERIENCE, REMOVE_EXPERIENCE,
    ADD_PROJECT, UPDATE_PROJECT, REMOVE_PROJECT,
    REORDER_SECTIONS,
    /** Swaps one technology for another everywhere it appears (skills, experiences, projects). */
    REPLACE_TECHNOLOGY,
    /** Deletes a whole optional section (its content becomes empty, so it is no longer rendered anywhere). */
    REMOVE_SECTION;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static AgentOperation from(String value) {
        return EnumValues.parse(AgentOperation.class, value);
    }

    public boolean isRemoval() {
        return this == REMOVE_EXPERIENCE || this == REMOVE_PROJECT || this == REMOVE_SECTION;
    }
}
