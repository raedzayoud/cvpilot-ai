package com.backend.backend.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Structured CV content. Stored as one JSONB document because it is always read and edited as a whole. */
public record CvProfile(
        @NotNull @Valid PersonalInfo personalInfo,
        @Size(max = 200) String professionalTitle,
        @Size(max = 4000) String summary,
        @Size(max = 100) List<@Size(max = 100) String> skills,
        @Size(max = 40) List<@Valid Experience> experiences,
        @Size(max = 20) List<@Valid EducationItem> education,
        @Size(max = 40) List<@Valid ProjectItem> projects,
        @Size(max = 40) List<@Valid CertificationItem> certifications,
        @Size(max = 20) List<@Valid LanguageItem> languages,
        /** Awards, rankings, competitions: one line each, as written in the CV. */
        @Size(max = 30) List<@Size(max = 500) String> achievements) {

    public CvProfile {
        personalInfo = personalInfo == null ? PersonalInfo.empty() : personalInfo;
        professionalTitle = Lists.text(professionalTitle);
        summary = Lists.text(summary);
        skills = Lists.strings(skills).stream().distinct().toList();
        experiences = Lists.of(experiences);
        education = Lists.of(education);
        projects = Lists.of(projects);
        certifications = Lists.of(certifications);
        languages = Lists.of(languages);
        achievements = Lists.strings(achievements);
    }

    public static CvProfile empty() {
        return new CvProfile(PersonalInfo.empty(), "", "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Every value in the profile, lowercased, used for "does the CV mention X" checks. */
    public String searchableText() {
        List<String> parts = new ArrayList<>();
        parts.add(personalInfo.fullName());
        parts.add(personalInfo.location());
        parts.addAll(personalInfo.links());
        parts.add(professionalTitle);
        parts.add(summary);
        parts.addAll(skills);
        for (Experience e : experiences) {
            parts.addAll(List.of(e.role(), e.company(), e.location()));
            parts.addAll(e.bullets());
            parts.addAll(e.technologies());
        }
        for (EducationItem e : education) parts.addAll(List.of(e.degree(), e.school(), e.details()));
        for (ProjectItem p : projects) {
            parts.addAll(List.of(p.name(), p.description(), p.link()));
            parts.addAll(p.technologies());
        }
        for (CertificationItem c : certifications) parts.addAll(List.of(c.name(), c.issuer()));
        for (LanguageItem l : languages) parts.add(l.name());
        parts.addAll(achievements);
        return String.join(" | ", parts.stream().filter(s -> s != null && !s.isBlank()).toList())
                .toLowerCase(Locale.ROOT);
    }

    /** Assigns ids to items the AI or the user left without one, so decisions can reference them. */
    public CvProfile withItemIds() {
        // Ids already in use, so a generated id never collides with one the user kept from an earlier save.
        Set<String> taken = new HashSet<>();
        Stream.of(experiences.stream().map(Experience::id), education.stream().map(EducationItem::id),
                        projects.stream().map(ProjectItem::id), certifications.stream().map(CertificationItem::id),
                        languages.stream().map(LanguageItem::id))
                .flatMap(s -> s).filter(id -> !id.isEmpty()).forEach(taken::add);
        AtomicInteger n = new AtomicInteger();
        Set<String> seen = new HashSet<>();
        Function<String, String> next = prefix -> {
            String id;
            do {
                id = prefix + n.incrementAndGet();
            } while (taken.contains(id));
            taken.add(id);
            return id;
        };
        // Empty or duplicate ids get a fresh one: decisions reference items by id.
        Predicate<String> needsId = id -> id.isEmpty() || !seen.add(id);
        return new CvProfile(
                personalInfo, professionalTitle, summary, skills,
                experiences.stream().map(e -> needsId.test(e.id())
                        ? e.withId(next.apply("exp-"))
                        : e).toList(),
                education.stream().map(e -> needsId.test(e.id())
                        ? new EducationItem(next.apply("edu-"), e.degree(), e.school(), e.location(), e.start(), e.end(), e.details())
                        : e).toList(),
                projects.stream().map(p -> needsId.test(p.id())
                        ? new ProjectItem(next.apply("prj-"), p.name(), p.description(), p.technologies(), p.link())
                        : p).toList(),
                certifications.stream().map(c -> needsId.test(c.id())
                        ? new CertificationItem(next.apply("cert-"), c.name(), c.issuer(), c.year())
                        : c).toList(),
                languages.stream().map(l -> needsId.test(l.id())
                        ? new LanguageItem(next.apply("lang-"), l.name(), l.level())
                        : l).toList(),
                achievements);
    }

    /**
     * CVs without a skills section still name technologies under each role and project; use those
     * (all taken from the CV itself) so matching and the tailored summary have skills to work with.
     */
    public CvProfile withSkillsFromTechnologies() {
        if (!skills.isEmpty()) return this;
        List<String> derived = Stream.concat(
                        experiences.stream().flatMap(e -> e.technologies().stream()),
                        projects.stream().flatMap(p -> p.technologies().stream()))
                .collect(java.util.stream.Collectors.toMap(s -> s.toLowerCase(Locale.ROOT), s -> s, (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values().stream().limit(60).toList();
        if (derived.isEmpty()) return this;
        return new CvProfile(personalInfo, professionalTitle, summary, derived, experiences, education, projects,
                certifications, languages, achievements);
    }

    public boolean isBlank() {
        return Stream.of(personalInfo.fullName(), professionalTitle, summary).allMatch(String::isBlank)
                && skills.isEmpty() && experiences.isEmpty() && education.isEmpty() && projects.isEmpty();
    }
}
