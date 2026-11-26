package com.backend.backend.model;

import jakarta.validation.constraints.Size;

public record EducationItem(
        @Size(max = 64) String id,
        @Size(max = 300) String degree,
        @Size(max = 300) String school,
        @Size(max = 200) String location,
        @Size(max = 40) String start,
        @Size(max = 40) String end,
        @Size(max = 1500) String details) {

    public EducationItem {
        id = Lists.text(id);
        degree = Lists.text(degree);
        school = Lists.text(school);
        location = Lists.text(location);
        start = Lists.text(start);
        end = Lists.text(end);
        details = Lists.text(details);
    }
}
