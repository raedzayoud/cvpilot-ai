package com.backend.backend.service.agent;

import static com.backend.backend.service.agent.ChangeDescriber.added;
import static com.backend.backend.service.agent.ChangeDescriber.jobRequirementsIn;
import static com.backend.backend.service.matching.TextMatcher.mentions;

import com.backend.backend.integration.ai.AiToolRequest.Tool;
import com.backend.backend.model.AnalysisInsights;
import com.backend.backend.model.CvDecision;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.CvSections;
import com.backend.backend.model.DecisionKind;
import com.backend.backend.model.Experience;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.ProjectItem;
import com.backend.backend.model.Relevance;
import com.backend.backend.model.agent.AgentFacts.ConfirmedFact;
import com.backend.backend.model.agent.AgentFacts.Evidence;
import com.backend.backend.model.agent.AgentFacts.JobTarget;
import com.backend.backend.model.agent.AgentOperation;
import com.backend.backend.model.agent.ProposedChange;
import com.backend.backend.service.ai.JobParsingService;
import com.backend.backend.service.matching.CustomizationEngine;
import com.backend.backend.service.matching.ScoringEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent's tools. The model decides which to call; each tool validates its input against the CV
 * and the user's own statements before anything is proposed. Tools never modify the CV: they only
 * create proposals, which the user accepts or rejects.
 */
@Slf4j
@Component
public class AgentToolbox {


    private final JsonMapper json;
    private final ClaimValidator validator;
    private final JobParsingService jobParser;
    private final CustomizationEngine engine;
    private final ScoringEngine scoring;

    public AgentToolbox(JsonMapper json, ClaimValidator validator, JobParsingService jobParser,
                        CustomizationEngine engine, ScoringEngine scoring) {
        this.json = json;
        this.validator = validator;
        this.jobParser = jobParser;
        this.engine = engine;
        this.scoring = scoring;
    }

    // ------------------------------------------------------------------ definitions

    public List<Tool> definitions() {
        return List.of(
                tool("analyze_job_description",
                        "Analyze a job description the user provided: extracts requirements and compares them with the CV "
                                + "(matched and missing skills, relevant experiences/projects, match score). Call this before tailoring.",
                        obj(Map.of("job_description", str("The full job description text")), "job_description")),
                tool("record_user_fact",
                        "Optional. Record a fact the user stated about themselves (e.g. 'I built a REST API "
                                + "with ASP.NET Core'), quote copied word for word. Skills the user states are already accepted; "
                                + "never use this for job description requirements.",
                        obj(Map.of("quote", str("Exact first-person excerpt of the user's message"),
                                "skills", arr("Technologies the user said they used, as written")), "quote", "skills")),
                tool("update_headline", "Propose a new professional title under the name.",
                        obj(Map.of("professional_title", str("New title"), "reason", str("Why, for the user")),
                                "professional_title", "reason")),
                tool("rewrite_summary", "Propose a rewritten professional summary based only on facts in the CV.",
                        obj(Map.of("summary", str("The full new summary"), "reason", str("Why, for the user")), "summary", "reason")),
                tool("set_skills", "Propose the complete, re-ordered technical skills list (add confirmed skills, remove irrelevant ones).",
                        obj(Map.of("skills", arr("Full ordered skills list"), "reason", str("Why, for the user")), "skills", "reason")),
                tool("update_experience",
                        "Propose a rewrite of one existing experience. Send only the fields that change; omitted fields "
                                + "keep their current value (e.g. only technologies for a tech swap). Do not change company, "
                                + "role, location or dates unless the user explicitly asked.",
                        obj(props(Map.of("experience_id", str("id of the experience from the CV context"),
                                "bullets", arr("Full new list of bullet points, only if they change"),
                                "technologies", arr("Full technologies line"),
                                "role", str("Only if the user asked to change it"),
                                "company", str("Only if the user asked to change it"),
                                "location", str("Only if the user asked to change it"),
                                "start_date", str("Only if the user asked to change it"),
                                "end_date", str("Only if the user asked to change it"),
                                "reason", str("Why, for the user"))), "experience_id", "reason")),
                tool("add_experience",
                        "Propose a new work experience the USER described. Requires role, company and start date stated by the user, "
                                + "and bullets based on what the user said they did. If anything is missing, do not call: ask the user.",
                        obj(props(Map.of("role", str("Job title as the user gave it"), "company", str("Company as the user gave it"),
                                "location", str("Location if given"), "start_date", str("As the user gave it"),
                                "end_date", str("As given, or Present"), "bullets", arr("Professional bullet points"),
                                "technologies", arr("Technologies the user said they used"), "reason", str("Why"))),
                                "role", "company", "start_date", "bullets", "reason")),
                tool("remove_experience", "Propose removing an experience (the user must approve).",
                        obj(Map.of("experience_id", str("id of the experience"), "reason", str("Why")), "experience_id", "reason")),
                tool("update_project",
                        "Propose a rewrite of one existing project. Send only the fields that change; omitted fields keep "
                                + "their current value.",
                        obj(props(Map.of("project_id", str("id of the project from the CV context"),
                                "description", str("New description, only if it changes"), "technologies", arr("Technologies line"),
                                "name", str("Only if the user asked to rename it"), "link", str("Only if given by the user"),
                                "reason", str("Why"))), "project_id", "reason")),
                tool("add_project",
                        "Propose a new project the USER described (name, what it does, technologies they used). "
                                + "If the name or details are missing, ask the user instead.",
                        obj(props(Map.of("name", str("Project name as the user gave it"), "description", str("What it does"),
                                "technologies", arr("Technologies the user said they used"), "link", str("Link if given"),
                                "reason", str("Why"))), "name", "description", "technologies", "reason")),
                tool("remove_project", "Propose removing a project (the user must approve).",
                        obj(Map.of("project_id", str("id of the project"), "reason", str("Why")), "project_id", "reason")),
                tool("reorder_sections", "Propose a new order of CV sections. Keys: " + String.join(", ", CvSections.DEFAULT_ORDER),
                        obj(Map.of("order", arr("Section keys in the new order"), "reason", str("Why")), "order", "reason")),
                tool("replace_technology",
                        "Replace one technology with another EVERYWHERE in the CV (skills, experience bullets and "
                                + "technologies, project descriptions and technologies). Use it for global swaps, never "
                                + "item by item.",
                        obj(Map.of("from", str("The technology to replace, as written"),
                                "to", str("The replacement technology"), "reason", str("Why, for the user")),
                                "from", "to", "reason")),
                tool("remove_section",
                        "Remove a whole section and all its content, heading included. Use it as soon as the user asks to "
                                + "remove/delete a section: their request is the confirmation. Keys: "
                                + String.join(", ", ChangeDescriber.REMOVABLE_SECTIONS),
                        obj(Map.of("section", str("Section key"), "reason", str("Why")), "section", "reason")));
    }

    // ------------------------------------------------------------------ execution

    public Map<String, Object> execute(String name, String argumentsJson, AgentState state) {
        state.getToolsUsed().add(name);
        JsonNode args;
        try {
            args = json.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
        } catch (JacksonException e) {
            return error("Arguments were not valid JSON.");
        }
        try {
            return switch (name) {
                case "analyze_job_description" -> analyzeJob(args, state);
                case "record_user_fact" -> recordFact(args, state);
                case "update_headline" -> updateHeadline(args, state);
                case "rewrite_summary" -> rewriteSummary(args, state);
                case "set_skills" -> setSkills(args, state);
                case "update_experience" -> updateExperience(args, state);
                case "add_experience" -> addExperience(args, state);
                case "remove_experience" -> removeExperience(args, state);
                case "update_project" -> updateProject(args, state);
                case "add_project" -> addProject(args, state);
                case "remove_project" -> removeProject(args, state);
                case "reorder_sections" -> reorder(args, state);
                case "replace_technology" -> replaceTechnology(args, state);
                case "remove_section" -> removeSection(text(args, "section"), text(args, "reason"), state);
                default -> error("Unknown tool " + name + ".");
            };
        } catch (RuntimeException e) {
            log.warn("Agent tool {} failed: {}", name, e.toString());
            return error("The tool failed: " + e.getMessage());
        }
    }

    private Map<String, Object> analyzeJob(JsonNode args, AgentState state) {
        String text = text(args, "job_description");
        if (text.length() < 30) return error("The job description is too short. Ask the user to paste the full posting.");
        JobPosition target = engine.mergePositions(jobParser.parse(text));
        CvProfile profile = state.getProfile();
        AnalysisInsights insights = scoring.compute(profile, target, "agent", Set.of());
        ClaimValidator.Corpus corpus = state.corpus();
        List<String> terms = Stream.of(target.requirements().technical(), target.requirements().optional(), target.requirements().tools())
                .flatMap(List::stream).distinct().toList();
        List<String> matched = terms.stream().filter(t -> validator.source(t, corpus) != null).toList();
        List<String> missing = terms.stream().filter(t -> validator.source(t, corpus) == null).toList();
        state.setJobTarget(new JobTarget(target.title(), target.requirements(), matched, missing, insights.score()));

        List<CvDecision> plan = engine.buildPlan(profile, target);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("job_title", target.title());
        result.put("match_score", insights.score());
        result.put("matched_skills", matched);
        result.put("missing_skills", missing);
        result.put("relevant_experiences", relevant(plan, DecisionKind.EXPERIENCE));
        result.put("relevant_projects", relevant(plan, DecisionKind.PROJECT));
        result.put("guidance", "Emphasize matched skills and relevant items. Missing skills must NOT be claimed; "
                + "mention them to the user and ask whether they have real experience with them.");
        return result;
    }

    private Map<String, Object> recordFact(JsonNode args, AgentState state) {
        String quote = text(args, "quote");
        List<String> skills = list(args, "skills");
        String normalizedQuote = normalize(quote);
        boolean verbatim = !normalizedQuote.isEmpty()
                && state.getUserMessages().stream().anyMatch(m -> normalize(m).contains(normalizedQuote));
        if (!verbatim) return error("The quote is not an exact excerpt of the user's messages.");
        boolean fromStatement = state.getUserStatements().stream().anyMatch(m -> normalize(m).contains(normalizedQuote));
        if (!fromStatement) {
            return error("The quote is not something the user said about themselves (e.g. a job requirement). "
                    + "Ask the user whether they personally have this experience.");
        }
        List<String> notInQuote = skills.stream().filter(s -> !ClaimValidator.backed(s, normalizedQuote)).toList();
        if (!notInQuote.isEmpty()) return error("These skills are not in the quote: " + String.join(", ", notInQuote));
        state.getFacts().add(new ConfirmedFact(quote.strip(), skills, Instant.now()));
        return Map.of("status", "ok", "recorded", skills);
    }

    private Map<String, Object> updateHeadline(JsonNode args, AgentState state) {
        String title = text(args, "professional_title");
        if (title.isEmpty() || title.length() > 120) return error("Provide a title of at most 120 characters.");
        CvProfile p = state.getProfile();
        ClaimValidator.Result check = validator.checkText(title, state.corpus());
        if (!check.ok()) return blocked(state, check.problems());
        return propose(state, change(AgentOperation.UPDATE_HEADLINE, "header", null, "Professional title",
                p.professionalTitle(), title, text(args, "reason"), state, check.evidence(),
                title, null, null, null, p.professionalTitle(), null, null, null));
    }

    private Map<String, Object> rewriteSummary(JsonNode args, AgentState state) {
        String summary = text(args, "summary");
        if (summary.length() < 20 || summary.length() > 1500) return error("The summary must be 20 to 1500 characters.");
        CvProfile p = state.getProfile();
        ClaimValidator.Result check = validator.checkText(summary, state.corpus());
        if (!check.ok()) return blocked(state, check.problems());
        return propose(state, change(AgentOperation.UPDATE_SUMMARY, "summary", null, "Professional summary",
                p.summary(), summary, text(args, "reason"), state, check.evidence(),
                summary, null, null, null, p.summary(), null, null, null));
    }

    private Map<String, Object> setSkills(JsonNode args, AgentState state) {
        List<String> skills = list(args, "skills").stream().distinct().limit(100).toList();
        // An empty list is a valid request: no skills means no Technical Skills section.
        if (skills.isEmpty()) return removeSection("skills", text(args, "reason"), state);
        CvProfile p = state.getProfile();
        ClaimValidator.Result check = validator.checkSkills(skills, state.corpus());
        if (!check.ok()) return blocked(state, check.problems());
        String original = String.join(", ", p.skills());
        String updated = String.join(", ", skills);
        List<String> removed = p.skills().stream().filter(s -> skills.stream().noneMatch(s::equalsIgnoreCase)).toList();
        return propose(state, new ProposedChange(AgentOperation.UPDATE_SKILLS, "skills", null, "Technical skills",
                original, updated, text(args, "reason"), jobRequirementsIn(updated, jobTerms(state)),
                skills.stream().filter(s -> p.skills().stream().noneMatch(s::equalsIgnoreCase)).toList(), removed,
                check.evidence(), null, skills, null, null, null, p.skills(), null, null));
    }

    private Map<String, Object> updateExperience(JsonNode args, AgentState state) {
        Experience before = experience(state, text(args, "experience_id"));
        if (before == null) return error("Unknown experience_id. Use an id from the CV context.");
        Experience after = new Experience(before.id(),
                orElse(args, "role", before.role()), orElse(args, "company", before.company()),
                orElse(args, "location", before.location()), orElse(args, "start_date", before.start()),
                orElse(args, "end_date", before.end()),
                args.has("bullets") ? list(args, "bullets") : before.bullets(),
                args.has("technologies") ? list(args, "technologies") : before.technologies());
        List<String> problems = new ArrayList<>(experienceFields(before, after, state, false));
        ClaimValidator.Result check = validator.checkText(itemText(after), state.corpus());
        problems.addAll(check.problems());
        problems.addAll(validator.checkSkills(after.technologies(), state.corpus()).problems());
        if (!problems.isEmpty()) return blocked(state, problems);
        return propose(state, change(AgentOperation.UPDATE_EXPERIENCE, "experience", before.id(),
                ChangeDescriber.join(" — ", before.role(), before.company()), ChangeDescriber.experience(before),
                ChangeDescriber.experience(after), text(args, "reason"), state, check.evidence(),
                null, null, after, null, null, null, before, null));
    }

    private Map<String, Object> addExperience(JsonNode args, AgentState state) {
        Experience after = new Experience("", text(args, "role"), text(args, "company"), text(args, "location"),
                text(args, "start_date"), text(args, "end_date"), list(args, "bullets"), list(args, "technologies"));
        List<String> problems = new ArrayList<>(experienceFields(null, after, state, true));
        if (after.bullets().isEmpty()) problems.add("No responsibilities given: ask the user what they did there");
        ClaimValidator.Result check = validator.checkText(itemText(after), state.corpus());
        problems.addAll(check.problems());
        problems.addAll(validator.checkSkills(after.technologies(), state.corpus()).problems());
        if (problems.isEmpty()) problems.addAll(validator.checkGrounded("new experience", String.join(" ", after.bullets()), state.corpus()));
        if (!problems.isEmpty()) return blocked(state, problems);
        return propose(state, change(AgentOperation.ADD_EXPERIENCE, "experience", null,
                "New: " + ChangeDescriber.join(" — ", after.role(), after.company()), "",
                ChangeDescriber.experience(after), text(args, "reason"), state, check.evidence(),
                null, null, after, null, null, null, null, null));
    }

    private Map<String, Object> removeExperience(JsonNode args, AgentState state) {
        Experience before = experience(state, text(args, "experience_id"));
        if (before == null) return error("Unknown experience_id.");
        return propose(state, change(AgentOperation.REMOVE_EXPERIENCE, "experience", before.id(),
                "Remove: " + ChangeDescriber.join(" — ", before.role(), before.company()), ChangeDescriber.experience(before), "",
                text(args, "reason"), state, List.of(), null, null, null, null, null, null, before, null));
    }

    private Map<String, Object> updateProject(JsonNode args, AgentState state) {
        ProjectItem before = project(state, text(args, "project_id"));
        if (before == null) return error("Unknown project_id. Use an id from the CV context.");
        ProjectItem after = new ProjectItem(before.id(), orElse(args, "name", before.name()),
                args.has("description") ? text(args, "description") : before.description(),
                args.has("technologies") ? list(args, "technologies") : before.technologies(), orElse(args, "link", before.link()));
        List<String> problems = new ArrayList<>(validator.checkField("project name", before.name(), after.name(), state.corpus(), true));
        problems.addAll(validator.checkField("project link", before.link(), after.link(), state.corpus(), false));
        ClaimValidator.Result check = validator.checkText(after.name() + " " + after.description(), state.corpus());
        problems.addAll(check.problems());
        problems.addAll(validator.checkSkills(after.technologies(), state.corpus()).problems());
        if (!problems.isEmpty()) return blocked(state, problems);
        return propose(state, change(AgentOperation.UPDATE_PROJECT, "projects", before.id(), before.name(),
                ChangeDescriber.project(before), ChangeDescriber.project(after), text(args, "reason"), state, check.evidence(),
                null, null, null, after, null, null, null, before));
    }

    private Map<String, Object> addProject(JsonNode args, AgentState state) {
        ProjectItem after = new ProjectItem("", text(args, "name"), text(args, "description"), list(args, "technologies"), text(args, "link"));
        List<String> problems = new ArrayList<>(validator.checkField("project name", null, after.name(), state.corpus(), true));
        problems.addAll(validator.checkField("project link", null, after.link(), state.corpus(), false));
        ClaimValidator.Result check = validator.checkText(after.description(), state.corpus());
        problems.addAll(check.problems());
        problems.addAll(validator.checkSkills(after.technologies(), state.corpus()).problems());
        if (problems.isEmpty()) problems.addAll(validator.checkGrounded("project description", after.description(), state.corpus()));
        if (!problems.isEmpty()) return blocked(state, problems);
        return propose(state, change(AgentOperation.ADD_PROJECT, "projects", null, "New project: " + after.name(), "",
                ChangeDescriber.project(after), text(args, "reason"), state, check.evidence(),
                null, null, null, after, null, null, null, null));
    }

    private Map<String, Object> removeProject(JsonNode args, AgentState state) {
        ProjectItem before = project(state, text(args, "project_id"));
        if (before == null) return error("Unknown project_id.");
        return propose(state, change(AgentOperation.REMOVE_PROJECT, "projects", before.id(), "Remove project: " + before.name(),
                ChangeDescriber.project(before), "", text(args, "reason"), state, List.of(),
                null, null, null, null, null, null, null, before));
    }

    private Map<String, Object> reorder(JsonNode args, AgentState state) {
        List<String> order = CvSections.normalize(list(args, "order"));
        if (order.equals(state.getSectionOrder())) return error("That is already the current order.");
        return propose(state, new ProposedChange(AgentOperation.REORDER_SECTIONS, "layout", null, "Section order",
                ChangeDescriber.order(state.getSectionOrder()), ChangeDescriber.order(order), text(args, "reason"),
                List.of(), List.of(), List.of(), List.of(), null, order, null, null, null, state.getSectionOrder(), null, null));
    }

    /**
     * Swaps one technology for another everywhere. The user named both values, so it is their own instruction:
     * like a removal, no claim validation is needed.
     */
    private Map<String, Object> replaceTechnology(JsonNode args, AgentState state) {
        String from = text(args, "from");
        String to = text(args, "to");
        if (from.isEmpty() || to.isEmpty()) return error("Provide both the technology to replace and its replacement.");
        if (from.equalsIgnoreCase(to)) return error("The technology to replace and its replacement are the same.");
        CvProfile before = state.getProfile();
        if (ChangeApplier.replaceTechnology(before, from, to).equals(before)) {
            return error("No '" + from + "' found in your CV.");
        }
        String reason = text(args, "reason");
        return propose(state, new ProposedChange(AgentOperation.REPLACE_TECHNOLOGY, "skills", null,
                "Replace " + from + " with " + to, from, to,
                reason.isEmpty() ? "You asked to replace " + from + " with " + to + "." : reason,
                List.of(), List.of(to), List.of(from), List.of(), null, null, null, null, null, null, null, null));
    }

    /**
     * Deletes a whole optional section. Nothing is invented, so no claim validation is needed; the section's
     * current text is kept as "original" so the user sees exactly what disappears.
     */
    private Map<String, Object> removeSection(String rawKey, String reason, AgentState state) {
        String key = rawKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (java.util.Set.of("technicalskills", "technologies", "technology", "techskills", "tech", "technical").contains(key)) {
            key = "skills";
        }
        if (!ChangeDescriber.REMOVABLE_SECTIONS.contains(key)) {
            return error("Section '" + rawKey + "' cannot be removed as a whole. Removable: "
                    + String.join(", ", ChangeDescriber.REMOVABLE_SECTIONS) + ". Remove individual experiences instead.");
        }
        String current = ChangeDescriber.section(state.getProfile(), key);
        if (current.isBlank()) return error("The " + CvSections.LABELS.get(key) + " section is already empty, so it is not shown.");
        return propose(state, new ProposedChange(AgentOperation.REMOVE_SECTION, key, null,
                "Remove section: " + CvSections.LABELS.get(key), current, "",
                reason.isBlank() ? "You asked to remove this section." : reason,
                List.of(), List.of(), key.equals("skills") ? state.getProfile().skills() : List.of(), List.of(),
                null, null, null, null, null, null, null, null));
    }

    // ------------------------------------------------------------------ helpers

    private List<String> experienceFields(Experience before, Experience after, AgentState state, boolean adding) {
        ClaimValidator.Corpus corpus = state.corpus();
        List<String> problems = new ArrayList<>();
        problems.addAll(validator.checkField("role", before == null ? null : before.role(), after.role(), corpus, true));
        problems.addAll(validator.checkField("company", before == null ? null : before.company(), after.company(), corpus, true));
        problems.addAll(validator.checkField("start date", before == null ? null : before.start(), after.start(), corpus, true));
        problems.addAll(validator.checkField("end date", before == null ? null : before.end(), after.end(), corpus, !adding));
        problems.addAll(validator.checkField("location", before == null ? null : before.location(), after.location(), corpus, false));
        return problems;
    }

    private ProposedChange change(AgentOperation op, String section, String targetId, String label, String original,
                                  String updated, String reason, AgentState state, List<Evidence> evidence,
                                  String text, List<String> list, Experience experience, ProjectItem project,
                                  String beforeText, List<String> beforeList, Experience beforeExperience, ProjectItem beforeProject) {
        ClaimValidator.Corpus corpus = state.corpus();
        // Technologies lines of experiences/projects are evidence too, not only the free text.
        List<String> technologies = experience != null ? experience.technologies() : project != null ? project.technologies() : List.of();
        if (!technologies.isEmpty()) {
            Map<String, Evidence> merged = new LinkedHashMap<>();
            Stream.concat(evidence.stream(), validator.checkSkills(technologies, corpus).evidence().stream())
                    .forEach(e -> merged.putIfAbsent(e.term().toLowerCase(Locale.ROOT), e));
            evidence = List.copyOf(merged.values());
        }
        List<String> updatedTerms = validator.technologiesIn(updated, corpus);
        List<String> originalTerms = validator.technologiesIn(original, corpus);
        return new ProposedChange(op, section, targetId, label, original == null ? "" : original, updated, reason,
                jobRequirementsIn(updated, jobTerms(state)), added(updatedTerms, original),
                originalTerms.stream().filter(t -> !mentions(updated.toLowerCase(Locale.ROOT), t)).toList(), evidence,
                text, list, experience, project, beforeText, beforeList, beforeExperience, beforeProject);
    }

    private static Map<String, Object> propose(AgentState state, ProposedChange change) {
        state.propose(change);
        return Map.of("status", "proposed", "label", change.label(),
                "note", "Shown to the user for approval. Do not repeat the full text in your reply.");
    }

    private static Map<String, Object> blocked(AgentState state, List<String> problems) {
        state.getBlockedClaims().addAll(problems);
        return Map.of("status", "rejected", "problems", problems,
                "instruction", "Nothing was proposed. Call the tool again WITHOUT the unverified parts (drop them; never "
                        + "replace them with other invented details). Only if the request cannot be done without that "
                        + "information, tell the user what is missing and ask for it.");
    }

    private static Map<String, Object> error(String message) {
        return Map.of("status", "error", "message", message);
    }

    private static List<Map<String, String>> relevant(List<CvDecision> plan, DecisionKind kind) {
        return plan.stream()
                .filter(d -> d.getKind() == kind && (d.getSuggestion() == Relevance.HIGHLIGHT || d.getSuggestion() == Relevance.KEEP))
                .map(d -> Map.of("id", d.getId().substring(d.getId().indexOf(':') + 1), "label", d.getLabel(), "why", d.getReason()))
                .toList();
    }

    private static List<String> jobTerms(AgentState state) {
        JobTarget job = state.getJobTarget();
        if (job == null) return List.of();
        return Stream.of(job.requirements().technical(), job.requirements().optional(), job.requirements().tools())
                .flatMap(List::stream).distinct().toList();
    }

    private static Experience experience(AgentState state, String id) {
        return state.getProfile().experiences().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    private static ProjectItem project(AgentState state, String id) {
        return state.getProfile().projects().stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    private static String itemText(Experience e) {
        return String.join(" ", Stream.concat(Stream.of(e.role(), e.company()), e.bullets().stream()).toList());
    }

    private static String text(JsonNode args, String field) {
        JsonNode node = args.path(field);
        return node.isString() ? node.asString().strip() : "";
    }

    private static String orElse(JsonNode args, String field, String fallback) {
        String value = text(args, field);
        return value.isEmpty() ? fallback : value;
    }

    private static List<String> list(JsonNode args, String field) {
        List<String> out = new ArrayList<>();
        JsonNode node = args.path(field);
        if (node.isArray()) {
            for (JsonNode item : node) if (item.isString() && !item.asString().isBlank()) out.add(item.asString().strip());
        } else if (node.isString() && !node.asString().isBlank()) {
            for (String part : node.asString().split("[,\\n]")) if (!part.isBlank()) out.add(part.strip());
        }
        return out;
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[\\s\"“”]+", " ").strip();
    }

    // JSON schema builders
    private static Tool tool(String name, String description, Map<String, Object> parameters) {
        return new Tool(name, description, parameters);
    }

    private static Map<String, Object> str(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> arr(String description) {
        return Map.of("type", "array", "items", Map.of("type", "string"), "description", description);
    }

    private static Map<String, Object> props(Map<String, Object> properties) {
        return properties;
    }

    private static Map<String, Object> obj(Map<String, Object> properties, String... required) {
        return Map.of("type", "object", "properties", properties, "required", List.of(required));
    }
}
