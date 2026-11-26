package com.backend.backend.model.agent;

import com.backend.backend.util.EnumValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * PENDING until the user decides. SUPERSEDED when a newer proposal targets the same item; STALE when
 * the item changed after the proposal was made, so applying it would overwrite newer content.
 */
public enum ProposalStatus {
    PENDING, ACCEPTED, REJECTED, SUPERSEDED, STALE;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static ProposalStatus from(String value) {
        return EnumValues.parse(ProposalStatus.class, value);
    }
}
