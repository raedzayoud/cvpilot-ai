package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum WeaknessStatus {
    NOT_MENTIONED,CONFIRMED_MISSING;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static WeaknessStatus from(String value) {
        return EnumValues.parse(WeaknessStatus.class, value);
    }
}
