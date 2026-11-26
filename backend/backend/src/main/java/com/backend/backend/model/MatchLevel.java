package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum MatchLevel {
    MATCHED,PARTIAL,MISSING,UNVERIFIED;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static MatchLevel from(String value) {
        return EnumValues.parse(MatchLevel.class, value);
    }
}
