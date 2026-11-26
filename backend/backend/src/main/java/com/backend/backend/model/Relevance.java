package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum Relevance {
    HIGHLIGHT,KEEP,DEPRIORITIZE,SUGGEST_REMOVE;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static Relevance from(String value) {
        return EnumValues.parse(Relevance.class, value);
    }
}
