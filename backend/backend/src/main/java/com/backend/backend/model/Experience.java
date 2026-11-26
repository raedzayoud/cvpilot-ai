package com.backend.backend.model;

import jakarta.validation.constraints.Size;
import java.util.List;

public record Experience(
        @Size(max = 64) String id,
        @Size(max = 200) String role,
        @Size(max = 200) String company,
        @Size(max = 200) String location,
        @Size(max = 40) String start,
        @Size(max = 40) String end,
        @Size(max = 30) List<@Size(max = 1500) String> bullets,
        /** The "Technologies:" line under the role, if the CV has one. */
        @Size(max = 40) List<@Size(max = 100) String> technologies) {

    public Experience {
        id = Lists.text(id);
        role = Lists.text(role);
        company = Lists.text(company);
        location = Lists.text(location);
        start = Lists.text(start);
        end = Lists.text(end);
        bullets = Lists.strings(bullets);
        technologies = Lists.strings(technologies);
    }

    public Experience withId(String newId) {
        return new Experience(newId, role, company, location, start, end, bullets, technologies);
    }
}
