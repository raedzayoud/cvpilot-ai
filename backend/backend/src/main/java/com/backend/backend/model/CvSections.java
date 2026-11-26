package com.backend.backend.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Section keys used for ordering the CV body (header is always first). */
public final class CvSections {

    public static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("summary", "Summary");
        LABELS.put("skills", "Technical Skills");
        LABELS.put("experience", "Professional Experience");
        LABELS.put("projects", "Technical Projects");
        LABELS.put("education", "Education");
        LABELS.put("certifications", "Certifications");
        LABELS.put("languages", "Languages");
        LABELS.put("achievements", "Achievements");
    }

    public static final List<String> DEFAULT_ORDER = List.copyOf(LABELS.keySet());

    private CvSections() {
    }

    /** Known keys in the given order, unknown/duplicate keys dropped, missing keys appended. */
    public static List<String> normalize(List<String> order) {
        List<String> result = new ArrayList<>();
        if (order != null) {
            for (String key : order) {
                String k = key == null ? "" : key.trim().toLowerCase();
                if (LABELS.containsKey(k) && !result.contains(k)) result.add(k);
            }
        }
        for (String key : DEFAULT_ORDER) if (!result.contains(key)) result.add(key);
        return List.copyOf(result);
    }
}
