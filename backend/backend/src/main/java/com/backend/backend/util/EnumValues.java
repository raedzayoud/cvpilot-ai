package com.backend.backend.util;

import java.util.Locale;

/** Enums travel as lowercase snake_case strings ("suggest_remove") to match the frontend contract. */
public final class EnumValues {

    private EnumValues() {
    }

    public static String toJson(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing value for " + type.getSimpleName());
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
