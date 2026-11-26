package com.backend.backend.service.agent;

import static com.backend.backend.service.matching.TextMatcher.mentions;

import com.backend.backend.model.agent.AgentFacts.Evidence;
import com.backend.backend.model.agent.AgentFacts.Source;
import com.backend.backend.service.matching.SkillDictionary;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Enforces factual accuracy on everything the agent proposes. A claim is backed only if it is in
 * the CV or was explicitly stated by the user:
 * <ul>
 *   <li>Technologies/skills: must appear in the CV or in a confirmed fact (an exact first-person quote
 *       from the user). A job description the user pasted never counts as evidence.</li>
 *   <li>Numbers (metrics, years, team sizes): must appear in the CV or in the user's own messages.</li>
 *   <li>Facts of an experience (company, role, dates, location): unchanged, or stated by the user.</li>
 *   <li>New entries: their wording must be grounded in what the user said or what the CV already says.</li>
 * </ul>
 */
@Component
public class ClaimValidator {

    /** Spelling variants that refer to the same technology. */
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("node.js", List.of("nodejs", "node")),
            Map.entry(".net", List.of("dotnet", "asp.net", ".net core")),
            Map.entry("postgresql", List.of("postgres")),
            Map.entry("javascript", List.of("js")),
            Map.entry("typescript", List.of("ts")),
            Map.entry("kubernetes", List.of("k8s")),
            Map.entry("rest apis", List.of("rest api", "restful")),
            Map.entry("ci/cd", List.of("cicd", "continuous integration")));

    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\d.])\\d+(?:[.,]\\d+)?");

    private static final Set<String> STOPWORDS = Set.of(
            "about", "after", "again", "their", "there", "these", "those", "which", "while", "would", "using",
            "with", "within", "through", "across", "other", "under", "where", "being", "based", "build", "built",
            "developed", "develop", "designed", "implemented", "created", "worked", "working", "responsible",
            "including", "various", "multiple", "several", "different", "project", "projects", "application",
            "applications", "system", "systems", "solution", "solutions", "features", "feature");

    public record Corpus(String cvText, String factsText, String userText, List<String> jobTerms) {

        public Corpus {
            cvText = lower(cvText);
            factsText = lower(factsText);
            userText = lower(userText);
            jobTerms = jobTerms == null ? List.of() : jobTerms;
        }
    }

    public record Result(List<String> problems, List<Evidence> evidence) {

        public boolean ok() {
            return problems.isEmpty();
        }
    }

    /** Skills and numbers in free text (summary, bullets, descriptions, headline). */
    public Result checkText(String text, Corpus corpus) {
        List<String> problems = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        for (String term : technologiesIn(text, corpus)) {
            Source source = source(term, corpus);
            if (source == null) {
                problems.add("\"" + term + "\" is not in the CV and the user has not confirmed it");
            } else {
                evidence.add(new Evidence(term, source));
            }
        }
        problems.addAll(unbackedNumbers(text, corpus));
        return new Result(problems, dedupe(evidence));
    }

    /** Each skill of a skills list must be backed by the CV or a confirmed fact. */
    public Result checkSkills(List<String> skills, Corpus corpus) {
        List<String> problems = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        for (String skill : skills) {
            Source source = source(skill, corpus);
            if (source == null) problems.add("\"" + skill + "\" is not in the CV and the user has not confirmed it");
            else evidence.add(new Evidence(skill, source));
        }
        return new Result(problems, dedupe(evidence));
    }

    /** A changed or new factual field (company, role, date, name) must be stated by the user or already in the CV. */
    public List<String> checkField(String label, String before, String after, Corpus corpus, boolean required) {
        String value = after == null ? "" : after.trim();
        if (value.isEmpty()) {
            return required ? List.of("Missing " + label + ": ask the user for it") : List.of();
        }
        if (before != null && value.equalsIgnoreCase(before.trim())) return List.of();
        String lower = value.toLowerCase(Locale.ROOT);
        if (corpus.userText().contains(lower) || corpus.cvText().contains(lower)) return List.of();
        if (isOngoing(lower) && Stream.of("present", "now", "current", "ongoing", "today", "aujourd", "actuel", "en cours")
                .anyMatch(corpus.userText()::contains)) {
            return List.of();
        }
        return List.of("The " + label + " \"" + value + "\" was not stated by the user: ask them to confirm it");
    }

    /**
     * New content must be grounded: at least a quarter of its distinctive words must come from what
     * the user said or what the CV says, so the agent rewrites facts instead of inventing them.
     */
    public List<String> checkGrounded(String label, String text, Corpus corpus) {
        List<String> words = contentWords(text);
        if (words.isEmpty()) return List.of("The " + label + " is empty: ask the user what they did");
        long grounded = words.stream().filter(w -> corpus.userText().contains(w) || corpus.cvText().contains(w)).count();
        return grounded * 4 >= words.size()
                ? List.of()
                : List.of("The " + label + " describes things the user has not told you: ask for their responsibilities, technologies and achievements");
    }

    /** Technologies mentioned in a text: known technology names plus the job's technical requirements. */
    public List<String> technologiesIn(String text, Corpus corpus) {
        String lower = lower(text);
        Set<String> terms = new LinkedHashSet<>(SkillDictionary.findIn(lower));
        corpus.jobTerms().stream().filter(t -> mentions(lower, t)).forEach(terms::add);
        // "Spring" inside "Spring Boot" is the same claim; keep the longest form.
        List<String> all = List.copyOf(terms);
        return all.stream().filter(t -> all.stream().noneMatch(o -> !o.equalsIgnoreCase(t)
                && o.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT)) && mentions(lower, o))).toList();
    }

    Source source(String term, Corpus corpus) {
        if (backed(term, corpus.cvText())) return Source.CV;
        if (backed(term, corpus.factsText())) return Source.USER_CONFIRMED;
        return null;
    }

    static boolean backed(String term, String text) {
        String t = lower(term).trim();
        if (t.isEmpty() || text.isEmpty()) return false;
        List<String> variants = new ArrayList<>(List.of(t));
        if (t.endsWith("s")) variants.add(t.substring(0, t.length() - 1));
        else variants.add(t + "s");
        ALIASES.forEach((canonical, aliases) -> {
            if (canonical.equals(t) || aliases.contains(t)) {
                variants.add(canonical);
                variants.addAll(aliases);
            }
        });
        return variants.stream().anyMatch(v -> mentions(text, v));
    }

    private static List<String> unbackedNumbers(String text, Corpus corpus) {
        List<String> problems = new ArrayList<>();
        Matcher m = NUMBER.matcher(text == null ? "" : text);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) {
            String number = m.group();
            if (!seen.add(number)) continue;
            // Digit boundaries only: "40" must match "40k" and "30%", but not "140" or "4.0".
            Pattern exact = Pattern.compile("(?<![\\d.,])" + Pattern.quote(number) + "(?![\\d]|[.,]\\d)");
            boolean known = exact.matcher(corpus.cvText()).find() || exact.matcher(corpus.userText()).find()
                    || exact.matcher(corpus.factsText()).find();
            if (!known) problems.add("The number " + number + " does not appear in the CV or in the user's messages");
        }
        return problems;
    }

    private static List<String> contentWords(String text) {
        return Stream.of(lower(text).split("[^\\p{L}\\p{N}+#.]+"))
                .map(w -> w.replaceAll("^\\.+|\\.+$", ""))
                .filter(w -> w.length() >= 5 && !STOPWORDS.contains(w))
                .distinct()
                .toList();
    }

    private static boolean isOngoing(String value) {
        return value.matches("present|now|current|ongoing|today|aujourd'hui|en cours|actuel");
    }

    private static List<Evidence> dedupe(List<Evidence> evidence) {
        Set<String> seen = new LinkedHashSet<>();
        return evidence.stream().filter(e -> seen.add(e.term().toLowerCase(Locale.ROOT))).toList();
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
