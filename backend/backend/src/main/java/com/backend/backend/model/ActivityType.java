package com.backend.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.backend.backend.util.EnumValues;

public enum ActivityType {
    CV_UPLOADED,CV_UPDATED,CV_DELETED,JOB_CREATED,ANALYSIS_CREATED,ANALYSIS_EXPORTED,ANALYSIS_DELETED,AGENT_SESSION_STARTED,AGENT_CHANGES_SAVED;

    @JsonValue
    public String value() {
        return EnumValues.toJson(this);
    }

    @JsonCreator
    public static ActivityType from(String value) {
        return EnumValues.parse(ActivityType.class, value);
    }
}
