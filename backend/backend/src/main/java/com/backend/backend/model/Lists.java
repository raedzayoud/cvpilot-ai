package com.backend.backend.model;

import java.util.List;
import java.util.Objects;

final class Lists {

    private Lists() {
    }

    /** Null-safe, trimmed, immutable copy; AI output and partial JSON may omit arrays. */
    static List<String> strings(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    static <T> List<T> of(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
