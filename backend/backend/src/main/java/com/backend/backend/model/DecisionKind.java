package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum DecisionKind {
    SKILL,EXPERIENCE,PROJECT;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static DecisionKind from(String value) {
        return EnumValues.parse(DecisionKind.class, value);
    }
}
