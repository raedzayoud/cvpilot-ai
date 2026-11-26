package com.backend.backend.model;

import jakarta.validation.constraints.Size;

public record LanguageItem(@Size(max = 64) String id, @Size(max = 100) String name, @Size(max = 100) String level) {

    public LanguageItem {
        id = Lists.text(id);
        name = Lists.text(name);
        level = Lists.text(level);
    }
}
