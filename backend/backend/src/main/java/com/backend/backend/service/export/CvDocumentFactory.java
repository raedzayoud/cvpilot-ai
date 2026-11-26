package com.backend.backend.service.export;

import com.backend.backend.model.CvProfile;
import com.backend.backend.model.Customization;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.PersonalInfo;
import com.backend.backend.model.SectionProposal;
import com.backend.backend.service.matching.CustomizationEngine;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Applies the approved customization to the profile. Experience and Projects stay structured (so the
 * template can lay out roles, dates, bullets and technologies) unless the user rewrote that section by
 * hand, in which case the approved text is used verbatim.
 */
@Component
public class CvDocumentFactory {

    private static final Map<String, String> KNOWN_SITES = Map.ofEntries(
            Map.entry("linkedin.com", "LinkedIn"), Map.entry("github.com", "GitHub"), Map.entry("gitlab.com", "GitLab"),
            Map.entry("leetcode.com", "LeetCode"), Map.entry("codeforces.com", "Codeforces"),
            Map.entry("hackerrank.com", "HackerRank"), Map.entry("kaggle.com", "Kaggle"), Map.entry("medium.com", "Medium"),
            Map.entry("stackoverflow.com", "Stack Overflow"), Map.entry("behance.net", "Behance"),
            Map.entry("dribbble.com", "Dribbble"), Map.entry("x.com", "X"), Map.entry("twitter.com", "Twitter"));

    private final CustomizationEngine engine;

    public CvDocumentFactory(CustomizationEngine engine) {
        this.engine = engine;
    }

    public CvDocument build(CvProfile profile, JobPosition target, Customization customization) {
        var decisions = customization.getDecisions();
        Map<String, String> generated = engine.buildSections(profile, target, decisions).stream()
                .collect(java.util.stream.Collectors.toMap(SectionProposal::getKey, SectionProposal::getProposed, (a, b) -> a));

        String experienceText = editedText(customization, generated, "experience");
        String projectsText = editedText(customization, generated, "projects");

        return new CvDocument(
                header(profile),
                approved(customization, "summary"),
                skills(customization, profile, decisions),
                experienceText == null ? engine.orderedExperiences(profile, target, decisions) : List.of(),
                experienceText,
                projectsText == null ? engine.orderedProjects(profile, decisions) : List.of(),
                projectsText,
                profile.education(),
                profile.certifications(),
                profile.languages(),
                profile.achievements(),
                com.backend.backend.model.CvSections.DEFAULT_ORDER);
    }

    /** The profile as it is (used by the AI Agent, whose edits are already applied to the profile). */
    public CvDocument fromProfile(CvProfile profile, List<String> sectionOrder) {
        return new CvDocument(header(profile), profile.summary(), profile.skills(), profile.experiences(), null,
                profile.projects(), null, profile.education(), profile.certifications(), profile.languages(),
                profile.achievements(), sectionOrder);
    }

    private List<String> skills(Customization customization, CvProfile profile, List<com.backend.backend.model.CvDecision> decisions) {
        SectionProposal section = customization.section("skills");
        if (section == null) return engine.orderedSkills(profile, decisions);
        return Arrays.stream(section.getProposed().split("[,\\n]")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String approved(Customization customization, String key) {
        SectionProposal section = customization.section(key);
        return section == null ? "" : section.getProposed().strip();
    }

    /** The approved text when it differs from what the plan generates, i.e. the user rewrote it; else null. */
    private static String editedText(Customization customization, Map<String, String> generated, String key) {
        SectionProposal section = customization.section(key);
        if (section == null) return null;
        return Objects.equals(normalize(section.getProposed()), normalize(generated.get(key))) ? null : section.getProposed().strip();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    static CvDocument.Header header(CvProfile profile) {
        PersonalInfo info = profile.personalInfo();
        List<CvDocument.Link> links = info.links().stream().map(CvDocumentFactory::link).toList();
        return new CvDocument.Header(info.fullName(), profile.professionalTitle(), links, info.email(), info.phone(), info.location());
    }

    /** "https://github.com/x" becomes GitHub (linked); a bare label like "Portfolio" stays a label. */
    static CvDocument.Link link(String raw) {
        String value = raw.strip();
        boolean looksLikeUrl = value.matches("(?i)(https?://)?([\\w-]+\\.)+[a-z]{2,}(/\\S*)?");
        if (!looksLikeUrl) return new CvDocument.Link(value, null);
        String url = value.matches("(?i)https?://.*") ? value : "https://" + value;
        String host = url.replaceFirst("(?i)^https?://", "").replaceFirst("^www\\.", "").replaceFirst("[/?#].*$", "")
                .toLowerCase(Locale.ROOT);
        String label = KNOWN_SITES.entrySet().stream()
                .filter(e -> host.equals(e.getKey()) || host.endsWith("." + e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse("Portfolio");
        return new CvDocument.Link(label, url);
    }
}
