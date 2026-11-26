package com.backend.backend.service.agent;

import static com.backend.backend.service.matching.TextMatcher.mentions;

import com.backend.backend.model.CvSections;
import com.backend.backend.model.Experience;
import com.backend.backend.model.ProjectItem;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Human-readable renderings of CV items and keyword deltas for the original-vs-updated comparison. */
final class ChangeDescriber {

    private ChangeDescriber() {
    }

    static String experience(Experience e) {
        if (e == null) return "";
        String head = join(" — ", e.role(), e.company());
        String meta = join(", ", e.location(), join(" – ", e.start(), e.end()));
        StringBuilder out = new StringBuilder(head).append(meta.isEmpty() ? "" : " (" + meta + ")");
        e.bullets().forEach(b -> out.append("\n• ").append(b));
        if (!e.technologies().isEmpty()) out.append("\nTechnologies: ").append(String.join(", ", e.technologies()));
        return out.toString();
    }

    static String project(ProjectItem p) {
        if (p == null) return "";
        StringBuilder out = new StringBuilder(p.name());
        if (!p.description().isBlank()) out.append("\n").append(p.description());
        if (!p.technologies().isEmpty()) out.append("\nTechnologies: ").append(String.join(", ", p.technologies()));
        if (!p.link().isBlank()) out.append("\n").append(p.link());
        return out.toString();
    }

    /** Optional sections the agent may delete entirely. Header, experience and education are not removable as a whole. */
    static final List<String> REMOVABLE_SECTIONS = List.of("summary", "skills", "projects", "certifications", "languages", "achievements");

    /** Plain-text rendering of a whole section, used to show what a section removal deletes and to detect staleness. */
    static String section(com.backend.backend.model.CvProfile p, String key) {
        return switch (key) {
            case "summary" -> p.summary();
            case "skills" -> String.join(", ", p.skills());
            case "projects" -> p.projects().stream().map(ChangeDescriber::project).collect(Collectors.joining("\n\n"));
            case "certifications" -> p.certifications().stream()
                    .map(c -> join(" — ", c.name(), c.issuer(), c.year())).collect(Collectors.joining("\n"));
            case "languages" -> p.languages().stream().map(l -> join(" ", l.name(), l.level())).collect(Collectors.joining("\n"));
            case "achievements" -> String.join("\n", p.achievements());
            default -> "";
        };
    }

    static String order(List<String> keys) {
        return keys.stream().map(k -> CvSections.LABELS.getOrDefault(k, k)).collect(Collectors.joining(" → "));
    }

    /** Terms in {@code updated} that were not in {@code original} (case-insensitive). */
    static List<String> added(List<String> updatedTerms, String original) {
        String lower = original == null ? "" : original.toLowerCase(Locale.ROOT);
        return updatedTerms.stream().filter(t -> !mentions(lower, t)).distinct().toList();
    }

    static List<String> jobRequirementsIn(String text, List<String> jobTerms) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return jobTerms.stream().filter(t -> mentions(lower, t)).distinct().toList();
    }

    static String join(String separator, String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(separator));
    }
}
