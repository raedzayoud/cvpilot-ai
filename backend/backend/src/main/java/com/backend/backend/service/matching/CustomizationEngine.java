package com.backend.backend.service.matching;

import static com.backend.backend.service.matching.TextMatcher.mentions;

import com.backend.backend.model.CvDecision;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.DecisionAction;
import com.backend.backend.model.DecisionKind;
import com.backend.backend.model.EducationItem;
import com.backend.backend.model.Experience;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.JobRequirements;
import com.backend.backend.model.ProjectItem;
import com.backend.backend.model.Relevance;
import com.backend.backend.model.SectionProposal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Deterministic CV tailoring: decides what to highlight, keep, lower or remove, builds the proposed
 * sections, and checks any text for claims the original CV cannot back. No AI is involved here, so
 * the anti-fabrication guarantee does not depend on model behavior.
 */
@Component
public class CustomizationEngine {

    private static final Map<String, List<String>> DOMAIN_TERMS = new LinkedHashMap<>();

    static {
        DOMAIN_TERMS.put("frontend", List.of("angular", "react", "vue", "svelte", "frontend", "typescript", "javascript", "html", "css"));
        DOMAIN_TERMS.put("mobile", List.of("flutter", "dart", "react native", "mobile", "android", "ios", "swift", "kotlin"));
        DOMAIN_TERMS.put("backend", List.of("java", "spring", "backend", "rest", "api", "microservices", "node", "python", "php", "ruby"));
        DOMAIN_TERMS.put("data", List.of("sql", "postgresql", "mysql", "mongodb", "data", "spark", "airflow", "power bi", "tableau", "etl"));
        DOMAIN_TERMS.put("devops", List.of("docker", "kubernetes", "ci/cd", "terraform", "aws", "azure", "linux", "devops", "jenkins"));
    }

    private static final Set<String> FOUNDATIONAL =
            Set.of("git", "rest apis", "agile", "scrum", "communication", "problem solving", "ownership");

    /** Claims that are commonly invented for enterprise postings; always checked. */
    private static final Map<String, String> UNAVAILABLE_CLAIMS =
            Map.of("oracle", "Oracle", "kubernetes", "Kubernetes", "terraform", "Terraform", "jenkins", "Jenkins");

    private static final Map<String, List<String>> TRANSFERABLE = Map.ofEntries(
            Map.entry("angular", List.of("React", "Vue", "Svelte", "TypeScript")),
            Map.entry("react", List.of("Angular", "Vue", "Svelte", "TypeScript")),
            Map.entry("vue", List.of("Angular", "React", "Svelte", "TypeScript")),
            Map.entry("svelte", List.of("Angular", "React", "Vue")),
            Map.entry("kubernetes", List.of("Docker")),
            Map.entry("oracle", List.of("PostgreSQL", "SQL", "MySQL")),
            Map.entry("sql server", List.of("PostgreSQL", "MySQL", "SQL")),
            Map.entry("mysql", List.of("PostgreSQL")),
            Map.entry("c#", List.of("Java", ".NET")),
            Map.entry(".net", List.of("C#", "Java")),
            Map.entry("python", List.of("Java", "JavaScript")),
            Map.entry("kotlin", List.of("Java")),
            Map.entry("spark", List.of("SQL")),
            Map.entry("node.js", List.of("JavaScript", "TypeScript")),
            Map.entry("ci/cd", List.of("Docker", "Git")),
            Map.entry("aws", List.of("Azure", "Docker")),
            Map.entry("azure", List.of("AWS", "Docker")));

    // ---------------------------------------------------------------- job helpers

    /** Collapses a multi-role announcement into one target, as the product compares against the whole posting. */
    public JobPosition mergePositions(List<JobPosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return new JobPosition("merged", "Job posting", "", JobRequirements.empty());
        }
        if (positions.size() == 1) return positions.getFirst();

        List<JobRequirements> reqs = positions.stream().map(JobPosition::requirements).toList();
        Integer years = reqs.stream().map(JobRequirements::yearsExperience).filter(Objects::nonNull).max(Integer::compare).orElse(null);
        return new JobPosition(
                "merged",
                positions.getFirst().title() + " +" + (positions.size() - 1) + " more roles",
                positions.size() + " roles in one posting.",
                new JobRequirements(
                        unique(reqs, JobRequirements::technical),
                        unique(reqs, JobRequirements::optional),
                        years,
                        reqs.stream().map(JobRequirements::education).filter(Objects::nonNull).findFirst().orElse(null),
                        unique(reqs, JobRequirements::responsibilities),
                        unique(reqs, JobRequirements::tools),
                        unique(reqs, JobRequirements::softSkills)));
    }

    /** Required technologies and tools the CV never mentions. */
    public List<String> missingSkills(CvProfile profile, JobPosition target) {
        String cvText = profile.searchableText();
        return Stream.concat(target.requirements().technical().stream(), target.requirements().tools().stream())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .filter(token -> !mentions(cvText, token))
                .toList();
    }

    /** Messages for every claim in {@code text} that names a skill absent from the CV. */
    public List<String> violationsFor(String text, CvProfile profile, JobPosition target) {
        String cvText = profile.searchableText();
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);

        Map<String, String> claims = new LinkedHashMap<>(UNAVAILABLE_CLAIMS);
        for (String skill : missingSkills(profile, target)) claims.put(skill.toLowerCase(Locale.ROOT), skill);
        target.requirements().optional().forEach(skill -> claims.putIfAbsent(skill.toLowerCase(Locale.ROOT), skill));

        return claims.entrySet().stream()
                .filter(e -> mentions(lower, e.getKey()) && !mentions(cvText, e.getKey()))
                .map(e -> "Mentions \"" + e.getValue() + "\", which does not appear anywhere in your CV.")
                .toList();
    }

    /** CV skills that transfer to a missing skill, used to frame honest "related experience" wording. */
    public List<String> transferableFor(String gap, CvProfile profile) {
        String cvText = profile.searchableText();
        return TRANSFERABLE.getOrDefault(gap.toLowerCase(Locale.ROOT), List.of()).stream()
                .filter(skill -> mentions(cvText, skill))
                .toList();
    }

    // ---------------------------------------------------------------- plan

    public List<CvDecision> buildPlan(CvProfile profile, JobPosition target) {
        List<String> domains = jobDomains(target);
        List<CvDecision> decisions = new ArrayList<>();
        List<String> relevantSkills = new ArrayList<>();

        List<String> skills = profile.skills();
        for (int i = 0; i < skills.size(); i++) {
            String skill = skills.get(i);
            Verdict verdict = skillVerdict(skill, target, domains);
            if (verdict.suggestion() == Relevance.HIGHLIGHT || verdict.suggestion() == Relevance.KEEP) relevantSkills.add(skill);
            decisions.add(new CvDecision("skill:" + i, DecisionKind.SKILL, skill, verdict.reason(),
                    verdict.suggestion(), initialAction(verdict.suggestion())));
        }

        for (Experience experience : profile.experiences()) {
            String text = String.join(" ", Stream.concat(Stream.of(experience.role()), experience.bullets().stream()).toList());
            int score = itemScore(text, target, domains, relevantSkills);
            Relevance suggestion = score >= 4 ? Relevance.HIGHLIGHT : score >= 1 ? Relevance.KEEP : Relevance.DEPRIORITIZE;
            String reason = switch (suggestion) {
                case HIGHLIGHT -> "Strong match with the job requirements.";
                case KEEP -> "Some relevant experience for this job.";
                default -> "Unrelated to this job; kept lower so your history stays intact.";
            };
            decisions.add(new CvDecision("exp:" + experience.id(), DecisionKind.EXPERIENCE,
                    join(" — ", experience.role(), experience.company()), reason, suggestion, initialAction(suggestion)));
        }

        for (ProjectItem project : profile.projects()) {
            String text = String.join(" ", Stream.concat(Stream.of(project.name(), project.description()), project.technologies().stream()).toList());
            int score = itemScore(text, target, domains, relevantSkills);
            Relevance suggestion = score >= 3 ? Relevance.HIGHLIGHT : score >= 1 ? Relevance.KEEP : Relevance.SUGGEST_REMOVE;
            String reason = switch (suggestion) {
                case HIGHLIGHT -> "Supports the job requirements.";
                case KEEP -> "Related to this job.";
                default -> "Adds little to this job; removal needs your approval.";
            };
            decisions.add(new CvDecision("prj:" + project.id(), DecisionKind.PROJECT, project.name(), reason, suggestion, initialAction(suggestion)));
        }
        return decisions;
    }

    // ---------------------------------------------------------------- sections

    /** Skills in display order: highlighted first, removed ones dropped. */
    public List<String> orderedSkills(CvProfile profile, List<CvDecision> decisions) {
        return orderByAction(
                IntStream.range(0, profile.skills().size()).boxed().toList(),
                i -> skillAction(decisions, i, profile.skills().get(i))).stream().map(i -> profile.skills().get(i)).toList();
    }

    /** Experiences in display order, each with its bullets ordered by relevance to the job. */
    public List<Experience> orderedExperiences(CvProfile profile, JobPosition target, List<CvDecision> decisions) {
        return orderByAction(profile.experiences(), e -> actionOf(decisions, "exp:" + e.id())).stream()
                .map(e -> new Experience(e.id(), e.role(), e.company(), e.location(), e.start(), e.end(),
                        orderBullets(e.bullets(), target), e.technologies()))
                .toList();
    }

    public List<ProjectItem> orderedProjects(CvProfile profile, List<CvDecision> decisions) {
        return orderByAction(profile.projects(), p -> actionOf(decisions, "prj:" + p.id()));
    }

    public List<SectionProposal> buildSections(CvProfile profile, JobPosition target, List<CvDecision> decisions) {
        List<String> skills = orderedSkills(profile, decisions);
        List<Experience> experiences = orderByAction(profile.experiences(), e -> actionOf(decisions, "exp:" + e.id()));
        List<ProjectItem> projects = orderedProjects(profile, decisions);

        String education = profile.education().stream().map(CustomizationEngine::formatEducation).collect(Collectors.joining("\n\n"));
        String personal = Stream.concat(
                        Stream.of(profile.personalInfo().fullName(), profile.personalInfo().email(),
                                profile.personalInfo().phone(), profile.personalInfo().location()),
                        profile.personalInfo().links().stream())
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" · "));

        return new ArrayList<>(List.of(
                section("summary", "Professional summary", profile.summary(), buildSummary(profile, decisions), false),
                section("skills", "Technical skills", String.join(", ", profile.skills()), String.join(", ", skills), false),
                section("experience", "Experience",
                        profile.experiences().stream().map(e -> formatExperience(e, target)).collect(Collectors.joining("\n\n")),
                        experiences.stream().map(e -> formatExperience(e, target)).collect(Collectors.joining("\n\n")), false),
                section("projects", "Projects",
                        profile.projects().stream().map(CustomizationEngine::formatProject).collect(Collectors.joining("\n\n")),
                        projects.stream().map(CustomizationEngine::formatProject).collect(Collectors.joining("\n\n")), false),
                section("education", "Education", education, education, true),
                section("personal", "Personal details", personal, personal, true)));
    }

    /**
     * Rebuilds sections after a plan change, keeping approvals whose text did not change. Sections the
     * change does not affect keep their current text, so user edits and coach additions survive.
     */
    public List<SectionProposal> rebuildSections(CvProfile profile, JobPosition target, List<CvDecision> previousDecisions,
                                                 List<CvDecision> decisions, List<SectionProposal> previous) {
        Map<String, SectionProposal> before = previous.stream().collect(Collectors.toMap(SectionProposal::getKey, s -> s, (a, b) -> a));
        Map<String, String> generatedBefore = buildSections(profile, target, previousDecisions).stream()
                .collect(Collectors.toMap(SectionProposal::getKey, SectionProposal::getProposed, (a, b) -> a));
        List<SectionProposal> next = buildSections(profile, target, decisions);
        for (SectionProposal section : next) {
            if (section.isLocked()) continue;
            SectionProposal old = before.get(section.getKey());
            if (old != null && Objects.equals(generatedBefore.get(section.getKey()), section.getProposed())) {
                section.setProposed(old.getProposed());
            }
            section.setViolations(new ArrayList<>(violationsFor(section.getProposed(), profile, target)));
            section.setApproved(old != null && old.isApproved()
                    && Objects.equals(old.getProposed(), section.getProposed())
                    && section.getViolations().isEmpty());
        }
        return next;
    }

    public List<SectionProposal> initialSections(CvProfile profile, JobPosition target, List<CvDecision> decisions) {
        List<SectionProposal> sections = buildSections(profile, target, decisions);
        for (SectionProposal section : sections) {
            if (!section.isLocked()) section.setViolations(new ArrayList<>(violationsFor(section.getProposed(), profile, target)));
        }
        return sections;
    }

    // ---------------------------------------------------------------- internals

    private record Verdict(Relevance suggestion, String reason) {
    }

    private static Verdict skillVerdict(String skill, JobPosition target, List<String> domains) {
        if (mentions(requiredText(target), skill)) return new Verdict(Relevance.HIGHLIGHT, "Required by this job.");
        if (mentions(String.join(" | ", target.requirements().optional()), skill)) {
            return new Verdict(Relevance.KEEP, "Listed by the job as a plus.");
        }
        List<String> shared = domainsOf(skill).stream().filter(domains::contains).toList();
        if (!shared.isEmpty()) return new Verdict(Relevance.KEEP, "Transferable " + shared.getFirst() + " skill for this job.");
        if (FOUNDATIONAL.contains(skill.toLowerCase(Locale.ROOT))) {
            return new Verdict(Relevance.DEPRIORITIZE, "Useful general skill, kept as secondary.");
        }
        String focus = domains.isEmpty() ? "this" : String.join("/", domains);
        return new Verdict(Relevance.SUGGEST_REMOVE, "Not required by the job and outside its " + focus + " focus.");
    }

    private static DecisionAction initialAction(Relevance suggestion) {
        return switch (suggestion) {
            case HIGHLIGHT -> DecisionAction.HIGHLIGHT;
            case KEEP -> DecisionAction.KEEP;
            case DEPRIORITIZE, SUGGEST_REMOVE -> DecisionAction.DEPRIORITIZE;
        };
    }

    private static List<String> domainsOf(String text) {
        return DOMAIN_TERMS.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(term -> mentions(text, term)))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static List<String> jobDomains(JobPosition target) {
        JobRequirements r = target.requirements();
        String text = String.join(" ", Stream.of(List.of(target.title()), r.technical(), r.tools(), r.responsibilities())
                .flatMap(List::stream).toList());
        return domainsOf(text);
    }

    private static String requiredText(JobPosition target) {
        return String.join(" | ", Stream.of(List.of(target.title()), target.requirements().technical(), target.requirements().tools())
                .flatMap(List::stream).toList());
    }

    private static int itemScore(String text, JobPosition target, List<String> domains, List<String> relevantSkills) {
        int score = 0;
        for (String term : requiredTerms(target)) if (mentions(text, term)) score += 2;
        for (String skill : relevantSkills) if (mentions(text, skill)) score += 1;
        for (String domain : domainsOf(text)) if (domains.contains(domain)) score += 1;
        return score;
    }

    private static List<String> requiredTerms(JobPosition target) {
        return Stream.concat(target.requirements().technical().stream(), target.requirements().tools().stream()).toList();
    }

    private static DecisionAction actionOf(List<CvDecision> decisions, String id) {
        return decisions.stream().filter(d -> d.getId().equals(id)).map(CvDecision::getAction).findFirst().orElse(DecisionAction.KEEP);
    }

    /**
     * Skill decisions are keyed by list position, which shifts when the CV profile is edited after the
     * analysis was created. Only trust the positional decision when its label still names this skill;
     * otherwise follow the decision made for the same skill name, or keep it.
     */
    private static DecisionAction skillAction(List<CvDecision> decisions, int index, String skill) {
        String id = "skill:" + index;
        return decisions.stream()
                .filter(d -> d.getKind() == DecisionKind.SKILL && id.equals(d.getId()) && skill.equalsIgnoreCase(d.getLabel()))
                .map(CvDecision::getAction).findFirst()
                .orElseGet(() -> decisions.stream()
                        .filter(d -> d.getKind() == DecisionKind.SKILL && skill.equalsIgnoreCase(d.getLabel()))
                        .map(CvDecision::getAction).findFirst().orElse(DecisionAction.KEEP));
    }

    private static <T> List<T> orderByAction(List<T> items, Function<T, DecisionAction> action) {
        record Entry<T>(T item, int index, DecisionAction action) {
        }
        List<Entry<T>> entries = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) entries.add(new Entry<>(items.get(i), i, action.apply(items.get(i))));
        return entries.stream()
                .filter(e -> e.action() != DecisionAction.REMOVE)
                .sorted(Comparator.<Entry<T>>comparingInt(e -> e.action().ordinal()).thenComparingInt(Entry::index))
                .map(Entry::item)
                .toList();
    }

    private static List<String> orderBullets(List<String> bullets, JobPosition target) {
        List<String> terms = requiredTerms(target);
        return IntStream.range(0, bullets.size()).boxed()
                .sorted(Comparator.<Integer>comparingLong(i -> -terms.stream().filter(t -> mentions(bullets.get(i), t)).count())
                        .thenComparingInt(i -> i))
                .map(bullets::get)
                .toList();
    }

    private static String formatExperience(Experience e, JobPosition target) {
        String head = join(", ", e.role(), e.company());
        String dates = join("–", e.start(), e.end());
        String meta = join(", ", dates, e.location());
        String bullets = String.join(" ", orderBullets(e.bullets(), target));
        String tech = e.technologies().isEmpty() ? "" : " Technologies: " + String.join(", ", e.technologies()) + ".";
        return head + (meta.isEmpty() ? "" : " (" + meta + ")") + (bullets.isEmpty() ? "" : " — " + bullets) + tech;
    }

    private static String formatProject(ProjectItem p) {
        String tech = p.technologies().isEmpty() ? "" : " (" + String.join(", ", p.technologies()) + ")";
        String link = p.link().isEmpty() ? "" : " — " + p.link();
        return p.name() + (p.description().isEmpty() ? "" : " — " + p.description()) + tech + link;
    }

    private static String formatEducation(EducationItem item) {
        String dates = join("–", item.start(), item.end());
        return join(", ", item.degree(), item.school()) + (dates.isEmpty() ? "" : " (" + dates + ")")
                + (item.details().isEmpty() ? "" : " — " + item.details());
    }

    private static String buildSummary(CvProfile profile, List<CvDecision> decisions) {
        List<String> skills = profile.skills();
        List<String> highlighted = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        List<String> lowered = new ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            switch (skillAction(decisions, i, skills.get(i))) {
                case HIGHLIGHT -> highlighted.add(skills.get(i));
                case KEEP -> kept.add(skills.get(i));
                case DEPRIORITIZE -> lowered.add(skills.get(i));
                case REMOVE -> { }
            }
        }
        List<String> focused = highlighted.stream().filter(s -> !FOUNDATIONAL.contains(s.toLowerCase(Locale.ROOT))).toList();
        List<String> top = Stream.of(focused, highlighted, kept, lowered).filter(l -> !l.isEmpty()).findFirst().orElse(List.of())
                .stream().limit(5).toList();

        Experience best = Stream.of(DecisionAction.HIGHLIGHT, DecisionAction.KEEP, DecisionAction.DEPRIORITIZE)
                .flatMap(action -> profile.experiences().stream().filter(e -> actionOf(decisions, "exp:" + e.id()) == action))
                .findFirst().orElse(null);

        List<String> parts = new ArrayList<>();
        if (!top.isEmpty()) {
            String title = profile.professionalTitle().isEmpty() ? "Professional" : profile.professionalTitle();
            parts.add(title + " with hands-on experience in " + String.join(", ", top) + ".");
        }
        if (best != null && !best.role().isEmpty()) {
            parts.add("Relevant work includes " + best.role() + (best.company().isEmpty() ? "" : " at " + best.company()) + ".");
        }
        boolean anyRemoved = decisions.stream().anyMatch(d -> d.getAction() == DecisionAction.REMOVE);
        String joined = String.join(" ", parts);
        return !joined.isEmpty() ? joined : anyRemoved ? "" : profile.summary();
    }

    private static SectionProposal section(String key, String label, String original, String proposed, boolean locked) {
        return new SectionProposal(key, label, original, proposed, new ArrayList<>(), new ArrayList<>(), locked, locked);
    }

    private static List<String> unique(List<JobRequirements> reqs, Function<JobRequirements, List<String>> field) {
        return reqs.stream().flatMap(r -> field.apply(r).stream()).distinct().toList();
    }

    static String join(String separator, String... values) {
        return Stream.of(values).filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(separator));
    }
}
