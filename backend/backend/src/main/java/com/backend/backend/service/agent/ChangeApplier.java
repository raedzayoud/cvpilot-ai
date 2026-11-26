package com.backend.backend.service.agent;

import com.backend.backend.model.CvProfile;
import com.backend.backend.model.CvSections;
import com.backend.backend.model.Experience;
import com.backend.backend.model.ProjectItem;
import com.backend.backend.model.agent.ProposedChange;
import com.backend.backend.service.matching.TextMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Applies an accepted proposal to the working copy. Refuses (StaleException) when the target no
 * longer matches what the proposal was based on, so an old proposal never overwrites newer edits.
 */
@Component
public class ChangeApplier {

    public static class StaleException extends RuntimeException {
        public StaleException(String message) {
            super(message);
        }
    }

    public record Applied(CvProfile profile, List<String> sectionOrder) {
    }

    public Applied apply(CvProfile p, List<String> order, ProposedChange c) {
        return switch (c.operation()) {
            case UPDATE_HEADLINE -> {
                same(p.professionalTitle(), c.beforeText(), "headline");
                yield new Applied(with(p, c.text(), p.summary(), p.skills(), p.experiences(), p.projects()), order);
            }
            case UPDATE_SUMMARY -> {
                same(p.summary(), c.beforeText(), "summary");
                yield new Applied(with(p, p.professionalTitle(), c.text(), p.skills(), p.experiences(), p.projects()), order);
            }
            case UPDATE_SKILLS -> {
                same(p.skills(), c.beforeList(), "skills");
                yield new Applied(with(p, p.professionalTitle(), p.summary(), c.list(), p.experiences(), p.projects()), order);
            }
            case ADD_EXPERIENCE -> {
                List<Experience> list = new ArrayList<>(p.experiences());
                list.addFirst(c.experience());
                yield new Applied(with(p, p.professionalTitle(), p.summary(), p.skills(), list, p.projects()).withItemIds(), order);
            }
            case UPDATE_EXPERIENCE, REMOVE_EXPERIENCE -> {
                List<Experience> list = new ArrayList<>(p.experiences());
                int index = indexOfExperience(list, c.targetId());
                same(list.get(index), c.beforeExperience(), "experience");
                if (c.operation() == com.backend.backend.model.agent.AgentOperation.REMOVE_EXPERIENCE) list.remove(index);
                else list.set(index, c.experience().withId(c.targetId()));
                yield new Applied(with(p, p.professionalTitle(), p.summary(), p.skills(), list, p.projects()), order);
            }
            case ADD_PROJECT -> {
                List<ProjectItem> list = new ArrayList<>(p.projects());
                list.addFirst(c.project());
                yield new Applied(with(p, p.professionalTitle(), p.summary(), p.skills(), p.experiences(), list).withItemIds(), order);
            }
            case UPDATE_PROJECT, REMOVE_PROJECT -> {
                List<ProjectItem> list = new ArrayList<>(p.projects());
                int index = indexOfProject(list, c.targetId());
                same(list.get(index), c.beforeProject(), "project");
                if (c.operation() == com.backend.backend.model.agent.AgentOperation.REMOVE_PROJECT) list.remove(index);
                else {
                    ProjectItem pr = c.project();
                    list.set(index, new ProjectItem(c.targetId(), pr.name(), pr.description(), pr.technologies(), pr.link()));
                }
                yield new Applied(with(p, p.professionalTitle(), p.summary(), p.skills(), p.experiences(), list), order);
            }
            case REORDER_SECTIONS -> {
                same(order, c.beforeList(), "section order");
                yield new Applied(p, CvSections.normalize(c.list()));
            }
            case REPLACE_TECHNOLOGY -> {
                yield new Applied(replaceTechnology(p, c.originalText(), c.updatedText()), order);
            }
            case REMOVE_SECTION -> {
                same(ChangeDescriber.section(p, c.section()), c.originalText(), CvSections.LABELS.getOrDefault(c.section(), c.section()));
                yield new Applied(withoutSection(p, c.section()), order);
            }
        };
    }

    /** The profile with one optional section emptied; empty sections are not rendered in preview or any export. */
    static CvProfile withoutSection(CvProfile p, String key) {
        return new CvProfile(p.personalInfo(), p.professionalTitle(),
                key.equals("summary") ? "" : p.summary(),
                key.equals("skills") ? List.of() : p.skills(),
                p.experiences(), p.education(),
                key.equals("projects") ? List.of() : p.projects(),
                key.equals("certifications") ? List.of() : p.certifications(),
                key.equals("languages") ? List.of() : p.languages(),
                key.equals("achievements") ? List.of() : p.achievements());
    }

    /**
     * Swaps one technology for another across skills, experience bullets + technologies and project
     * descriptions + technologies. Works on the current working copy, so it needs no stale snapshot.
     */
    static CvProfile replaceTechnology(CvProfile p, String from, String to) {
        List<Experience> experiences = p.experiences().stream()
                .map(e -> new Experience(e.id(), e.role(), e.company(), e.location(), e.start(), e.end(),
                        e.bullets().stream().map(b -> TextMatcher.replace(b, from, to)).toList(),
                        replaceAll(e.technologies(), from, to)))
                .toList();
        List<ProjectItem> projects = p.projects().stream()
                .map(pr -> new ProjectItem(pr.id(), pr.name(), TextMatcher.replace(pr.description(), from, to),
                        replaceAll(pr.technologies(), from, to), pr.link()))
                .toList();
        return with(p, p.professionalTitle(), p.summary(), replaceAll(p.skills(), from, to), experiences, projects);
    }

    private static List<String> replaceAll(List<String> values, String from, String to) {
        return values.stream().map(v -> TextMatcher.replace(v, from, to)).distinct().toList();
    }

    static CvProfile with(CvProfile p, String title, String summary, List<String> skills,
                          List<Experience> experiences, List<ProjectItem> projects) {
        return new CvProfile(p.personalInfo(), title, summary, skills, experiences, p.education(), projects,
                p.certifications(), p.languages(), p.achievements());
    }

    private static int indexOfExperience(List<Experience> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        throw new StaleException("That experience is no longer in the CV.");
    }

    private static int indexOfProject(List<ProjectItem> list, String id) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(id)) return i;
        throw new StaleException("That project is no longer in the CV.");
    }

    private static void same(Object current, Object before, String what) {
        if (!Objects.equals(current, before)) {
            throw new StaleException("The " + what + " changed after this proposal was made. Ask the agent to regenerate it.");
        }
    }
}
