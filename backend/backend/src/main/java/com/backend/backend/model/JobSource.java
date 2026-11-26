package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum JobSource {
    PASTE,PDF,DOCX;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static JobSource from(String value) {
        return EnumValues.parse(JobSource.class, value);
    }
}
