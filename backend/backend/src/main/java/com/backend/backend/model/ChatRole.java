package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum ChatRole {
    BOT,USER;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static ChatRole from(String value) {
        return EnumValues.parse(ChatRole.class, value);
    }
}
