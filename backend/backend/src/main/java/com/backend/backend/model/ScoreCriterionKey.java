package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum ScoreCriterionKey {
    TECHNICAL,EXPERIENCE,PROJECTS,EDUCATION,OTHER;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static ScoreCriterionKey from(String value) {
        return EnumValues.parse(ScoreCriterionKey.class, value);
    }
}
