package com.backend.backend.model;

import jakarta.validation.constraints.Size;
import java.util.List;

public record ProjectItem(
        @Size(max = 64) String id,
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Size(max = 40) List<@Size(max = 100) String> technologies,
        @Size(max = 300) String link) {

    public ProjectItem {
        id = Lists.text(id);
        name = Lists.text(name);
        description = Lists.text(description);
        technologies = Lists.strings(technologies);
        link = Lists.text(link);
    }
}
