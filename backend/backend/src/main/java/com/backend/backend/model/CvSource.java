package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum CvSource {
    DOCX,PDF,TEX;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static CvSource from(String value) {
        return EnumValues.parse(CvSource.class, value);
    }
}
