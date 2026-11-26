package com.backend.backend.service.export;

import com.backend.backend.model.CertificationItem;
import com.backend.backend.model.EducationItem;
import com.backend.backend.model.Experience;
import com.backend.backend.model.LanguageItem;
import com.backend.backend.model.ProjectItem;
import java.util.List;

/**
 * Format-neutral, structured content of an exported CV: the approved customization applied to the
 * profile. Renderers (LaTeX, DOCX, PDF) only lay it out; they never decide what goes in.
 *
 * @param experienceText non-null when the user rewrote the Experience section by hand; renderers
 *                       then print that text instead of the structured {@code experiences}
 * @param projectsText   same, for the Projects section
 */
public record CvDocument(
        Header header,
        String summary,
        List<String> skills,
        List<Experience> experiences,
        String experienceText,
        List<ProjectItem> projects,
        String projectsText,
        List<EducationItem> education,
        List<CertificationItem> certifications,
        List<LanguageItem> languages,
        List<String> achievements,
        /** Section keys in display order (see {@link com.backend.backend.model.CvSections}). */
        List<String> sectionOrder) {

    public CvDocument {
        sectionOrder = com.backend.backend.model.CvSections.normalize(sectionOrder);
    }

    public record Header(String name, String title, List<Link> links, String email, String phone, String location) {
    }

    /** {@code url} is null when the CV only had a label (e.g. "Portfolio" without an address). */
    public record Link(String label, String url) {
    }

    public static List<String> paragraphs(String text) {
        if (text == null) return List.of();
        return java.util.Arrays.stream(text.split("\\n\\s*\\n")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
