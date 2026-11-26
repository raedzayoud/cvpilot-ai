package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum DecisionAction {
    HIGHLIGHT,KEEP,DEPRIORITIZE,REMOVE;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static DecisionAction from(String value) {
        return EnumValues.parse(DecisionAction.class, value);
    }
}
