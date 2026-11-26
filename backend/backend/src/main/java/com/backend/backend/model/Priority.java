package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum Priority {
    HIGH,MEDIUM,LOW;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static Priority from(String value) {
        return EnumValues.parse(Priority.class, value);
    }
}
