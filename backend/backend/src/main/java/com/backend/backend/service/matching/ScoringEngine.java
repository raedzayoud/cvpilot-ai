package com.backend.backend.service.matching;

import static com.backend.backend.service.matching.TextMatcher.mentions;

import com.backend.backend.model.AnalysisInsights;
import com.backend.backend.model.AnalysisInsights.CriterionEvidence;
import com.backend.backend.model.AnalysisInsights.ScoreCriterion;
import com.backend.backend.model.AnalysisInsights.SkillRecommendation;
import com.backend.backend.model.AnalysisInsights.Weakness;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.Experience;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.MatchLevel;
import com.backend.backend.model.Priority;
import com.backend.backend.model.ProjectItem;
import com.backend.backend.model.ScoreCriterionKey;
import com.backend.backend.model.WeaknessStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Explainable match score (0-100) with weighted criteria. Deterministic on purpose: the same CV and
 * job always give the same score, and every point is backed by visible evidence.
 */
@Component
public class ScoringEngine {

    public static final String MATCH_DISCLAIMER =
            "Estimated match based on your CV and this job. It is not a probability of being hired.";

    private static final int W_TECHNICAL = 35;
    private static final int W_EXPERIENCE = 25;
    private static final int W_PROJECTS = 20;
    private static final int W_EDUCATION = 10;
    private static final int W_OTHER = 10;

    private static final Pattern MEASURABLE = Pattern.compile("\\d|%");

    private static final Map<String, List<String>> SKILL_RELATIONS = Map.ofEntries(
            Map.entry("angular", List.of("typescript", "javascript")),
            Map.entry("react", List.of("angular", "typescript")),
            Map.entry("react native", List.of("flutter", "angular")),
            Map.entry("vue", List.of("angular", "typescript")),
            Map.entry("node.js", List.of("javascript", "typescript")),
            Map.entry("javascript", List.of("typescript")),
            Map.entry("ngrx", List.of("angular")),
            Map.entry("python", List.of("java", "javascript")),
            Map.entry("c#", List.of("java")),
            Map.entry(".net", List.of("java", "c#")),
            Map.entry("kubernetes", List.of("docker")),
            Map.entry("terraform", List.of("docker", "aws")),
            Map.entry("aws", List.of("docker")),
            Map.entry("azure", List.of("docker")),
            Map.entry("oracle jde", List.of("sql", "postgresql")),
            Map.entry("oracle ebs", List.of("sql")),
            Map.entry("sql", List.of("postgresql")),
            Map.entry("spark", List.of("sql", "java")),
            Map.entry("airflow", List.of("python", "sql")),
            Map.entry("power bi", List.of("sql")),
            Map.entry("tableau", List.of("sql")),
            Map.entry("kafka", List.of("java", "microservices")),
            Map.entry("docker", List.of("linux", "kubernetes")));

    private static final Map<String, String> ACTIVITY_HINTS = Map.ofEntries(
            Map.entry("ngrx", "Learn NgRx state management and build a small Angular app using NgRx."),
            Map.entry("kubernetes", "Deploy a small containerized app to a local Kubernetes cluster (kind or minikube)."),
            Map.entry("python", "Build a small API or automation script in Python and add it to your portfolio."),
            Map.entry("spark", "Process a public dataset with Spark and publish the notebook."),
            Map.entry("airflow", "Schedule a small ETL pipeline with Airflow on a public dataset."),
            Map.entry("power bi", "Build a Power BI dashboard from a public dataset."),
            Map.entry("tableau", "Recreate a dashboard in Tableau from a public dataset."),
            Map.entry("aws", "Deploy a small app on the AWS free tier and document the architecture."),
            Map.entry("azure", "Deploy a small app on Azure and document the architecture."),
            Map.entry("terraform", "Provision a small sandbox environment with Terraform and commit the config."),
            Map.entry("c#", "Build a small service in C# and .NET and publish it."),
            Map.entry(".net", "Build a small Web API with ASP.NET Core and publish it."),
            Map.entry("sql", "Practice SQL on a public dataset and publish a few analytical queries."));

    public AnalysisInsights compute(CvProfile profile, JobPosition target, String analysisId, Set<String> declined) {
        String cvText = profile.searchableText();
        List<String> req = target.requirements().technical();
        List<String> optional = target.requirements().optional();
        List<String> tools = target.requirements().tools();
        List<String> softSkills = target.requirements().softSkills();
        List<String> requiredAndTools = Stream.concat(req.stream(), tools.stream()).toList();

        List<CriterionEvidence> reqEvidence = req.stream()
                .map(t -> new CriterionEvidence(t, levelFor(mentions(cvText, t), t, declined))).toList();
        List<CriterionEvidence> optionalEvidence = optional.stream()
                .map(t -> new CriterionEvidence(t, mentions(cvText, t) ? MatchLevel.MATCHED : MatchLevel.UNVERIFIED)).toList();

        long reqMatched = reqEvidence.stream().filter(e -> e.level() == MatchLevel.MATCHED).count();
        long optionalMatched = optionalEvidence.stream().filter(e -> e.level() == MatchLevel.MATCHED).count();
        double techRatio = req.isEmpty()
                ? ratio(optionalMatched, optional.size())
                : 0.75 * ratio(reqMatched, req.size()) + 0.25 * ratio(optionalMatched, optional.size());

        List<Experience> experiences = profile.experiences();
        List<Experience> relevantExperience = experiences.stream()
                .filter(e -> anyMentioned(String.join(" ", Stream.concat(Stream.of(e.role(), e.company()), e.bullets().stream()).toList()), requiredAndTools))
                .toList();
        List<Experience> measurableExperience = experiences.stream()
                .filter(e -> e.bullets().stream().anyMatch(b -> MEASURABLE.matcher(b).find())).toList();
        double expRatio = experiences.isEmpty() ? 0
                : 0.7 * ratio(relevantExperience.size(), experiences.size()) + 0.3 * ratio(measurableExperience.size(), experiences.size());

        List<ProjectItem> projects = profile.projects();
        List<ProjectItem> relevantProjects = projects.stream()
                .filter(p -> anyMentioned(String.join(" ", Stream.concat(Stream.of(p.name(), p.description()), p.technologies().stream()).toList()), requiredAndTools))
                .toList();
        double projectRatio = projects.isEmpty() ? 0 : ratio(relevantProjects.size(), projects.size());

        double educationRatio = (profile.education().isEmpty() ? 0 : 0.7) + (profile.certifications().isEmpty() ? 0 : 0.3);

        List<String> otherTokens = Stream.concat(tools.stream(), softSkills.stream()).toList();
        long otherMatched = otherTokens.stream().filter(t -> mentions(cvText, t)).count();
        double otherRatio = ratio(otherMatched, otherTokens.size());

        List<CriterionEvidence> educationEvidence = new ArrayList<>();
        profile.education().forEach(e -> educationEvidence.add(
                new CriterionEvidence(CustomizationEngine.join(", ", e.degree(), e.school()), MatchLevel.MATCHED)));
        profile.certifications().forEach(c -> educationEvidence.add(
                new CriterionEvidence(c.name() + (c.issuer().isEmpty() ? "" : " (" + c.issuer() + ")"), MatchLevel.MATCHED)));

        List<CriterionEvidence> technicalEvidence = Stream.concat(reqEvidence.stream(), optionalEvidence.stream()).toList();
        List<ScoreCriterion> criteria = List.of(
                criterion(ScoreCriterionKey.TECHNICAL, "Technical skills", W_TECHNICAL, techRatio, technicalEvidence),
                criterion(ScoreCriterionKey.EXPERIENCE, "Professional experience", W_EXPERIENCE, expRatio,
                        experiences.stream().map(e -> new CriterionEvidence(
                                CustomizationEngine.join(" — ", e.role(), e.company()),
                                relevantExperience.contains(e)
                                        ? (measurableExperience.contains(e) ? MatchLevel.MATCHED : MatchLevel.PARTIAL)
                                        : MatchLevel.UNVERIFIED)).toList()),
                criterion(ScoreCriterionKey.PROJECTS, "Projects", W_PROJECTS, projectRatio,
                        projects.stream().map(p -> new CriterionEvidence(p.name(),
                                relevantProjects.contains(p) ? MatchLevel.MATCHED : MatchLevel.UNVERIFIED)).toList()),
                criterion(ScoreCriterionKey.EDUCATION, "Education & certifications", W_EDUCATION, educationRatio, educationEvidence),
                criterion(ScoreCriterionKey.OTHER, "Other requirements", W_OTHER, otherRatio,
                        otherTokens.stream().map(t -> new CriterionEvidence(t,
                                mentions(cvText, t) ? MatchLevel.MATCHED : MatchLevel.UNVERIFIED)).toList()));

        int score = Math.max(0, Math.min(100, criteria.stream().mapToInt(ScoreCriterion::points).sum()));

        List<String> matchedRequired = req.stream().filter(t -> mentions(cvText, t)).toList();
        LinkedHashSet<String> strengths = new LinkedHashSet<>();
        matchedRequired.stream().limit(4).forEach(t -> strengths.add("Covers required " + t));
        if (techRatio >= 0.6) strengths.add("Strong technical alignment with this job");
        if (!relevantExperience.isEmpty()) {
            strengths.add(relevantExperience.size() + " experience" + (relevantExperience.size() > 1 ? "s" : "") + " relevant to this role");
        }
        if (!relevantProjects.isEmpty()) {
            strengths.add(relevantProjects.size() + " project" + (relevantProjects.size() > 1 ? "s" : "") + " matching the job");
        }
        if (!measurableExperience.isEmpty()) strengths.add("Experience includes measurable results");

        List<Weakness> weaknesses = new ArrayList<>();
        String title = target.title();
        req.stream().filter(t -> !mentions(cvText, t)).limit(3).forEach(token -> {
            WeaknessStatus status = statusFor(token, declined);
            boolean confirmed = status == WeaknessStatus.CONFIRMED_MISSING;
            weaknesses.add(new Weakness(
                    "weak:tech:" + token.toLowerCase(Locale.ROOT),
                    confirmed ? token + " confirmed missing" : token + " not mentioned in your CV",
                    confirmed ? "You told me you do not have " + token + ", and this job requires it."
                            : "The job requires " + token + ", but it does not appear anywhere in your CV.",
                    "\"" + title + "\" lists " + token + " as a requirement, so reviewers will look for evidence of it.",
                    Priority.HIGH,
                    confirmed ? "Build demonstrable " + token + " experience, then add it to a project (see Skills to develop)."
                            : "If you have used " + token + ", add a concrete example; otherwise follow the " + token
                            + " learning path under Skills to develop.",
                    status));
        });

        if (!experiences.isEmpty() && measurableExperience.size() < experiences.size()) {
            weaknesses.add(new Weakness("weak:experience:measurable", "Experience lacks measurable results",
                    "Some experience bullets describe responsibilities without outcomes.",
                    "\"" + title + "\" rewards proven impact; numbers make relevance convincing.",
                    Priority.MEDIUM, "Add one metric per bullet (scale, %, time saved, users served).", WeaknessStatus.NOT_MENTIONED));
        }

        String summary = profile.summary();
        boolean summaryHasRelevantTech = Stream.concat(req.stream(), optional.stream()).anyMatch(t -> mentions(summary, t));
        if (!summary.isEmpty() && !summaryHasRelevantTech) {
            weaknesses.add(new Weakness("weak:summary", "Professional summary is too generic",
                    "The summary does not name any technology this job asks for.",
                    "A summary that echoes \"" + title + "\" is read first.",
                    Priority.LOW,
                    "Rewrite the opening to name your strongest matching skills"
                            + (matchedRequired.isEmpty() ? "" : " (e.g., " + String.join(", ", matchedRequired.stream().limit(3).toList()) + ")") + ".",
                    WeaknessStatus.NOT_MENTIONED));
        }

        if (!projects.isEmpty() && relevantProjects.isEmpty()) {
            weaknesses.add(new Weakness("weak:projects", "No project shows the required technologies",
                    "None of your projects mention the technologies this job requires.",
                    "\"" + title + "\" expects proof of hands-on work in these technologies.",
                    Priority.MEDIUM, "Add or reframe one project that uses the required technologies.", WeaknessStatus.NOT_MENTIONED));
        }

        optional.stream().filter(t -> !mentions(cvText, t)).limit(2).forEach(token -> weaknesses.add(new Weakness(
                "weak:optional:" + token.toLowerCase(Locale.ROOT),
                token + " not demonstrated",
                "The job lists " + token + " as a plus; your CV does not show it.",
                "\"" + title + "\" treats " + token + " as helpful, so it could separate you from other candidates.",
                Priority.LOW,
                "Mention " + token + " only if you have used it, or follow the " + token + " learning path.",
                statusFor(token, declined))));

        List<Weakness> topWeaknesses = weaknesses.stream()
                .sorted(Comparator.comparingInt(w -> w.severity().ordinal()))
                .limit(6).toList();

        List<String> improvements = new ArrayList<>(topWeaknesses.stream().map(Weakness::improvement).distinct().limit(5).toList());
        if (improvements.isEmpty()) {
            improvements.add("Your CV already aligns well with this job; keep the details specific and measurable.");
        }

        List<SkillRecommendation> recommendations = Stream.concat(req.stream(), optional.stream()).distinct()
                .filter(t -> !mentions(cvText, t))
                .limit(4)
                .map(skill -> new SkillRecommendation(
                        "rec:" + skill.toLowerCase(Locale.ROOT),
                        skill,
                        "\"" + title + "\" asks for " + skill + "; hands-on proof would strengthen your match.",
                        relationFor(skill, profile.skills()),
                        req.contains(skill) ? Priority.HIGH : Priority.MEDIUM,
                        ACTIVITY_HINTS.getOrDefault(skill.toLowerCase(Locale.ROOT),
                                "Take a focused " + skill + " course and ship one small project that uses it.")))
                .toList();

        return new AnalysisInsights(analysisId, score, criteria, strengths.stream().limit(6).toList(),
                topWeaknesses, improvements, recommendations, MATCH_DISCLAIMER);
    }

    private static ScoreCriterion criterion(ScoreCriterionKey key, String label, int weight, double ratio, List<CriterionEvidence> evidence) {
        double bounded = Math.max(0, Math.min(1, ratio));
        return new ScoreCriterion(key, label, weight, bounded, (int) Math.round(bounded * weight), evidence);
    }

    private static double ratio(long matched, long total) {
        return total == 0 ? 1 : (double) matched / total;
    }

    private static boolean anyMentioned(String text, List<String> tokens) {
        return tokens.stream().anyMatch(t -> mentions(text, t));
    }

    private static MatchLevel levelFor(boolean matched, String token, Set<String> declined) {
        if (matched) return MatchLevel.MATCHED;
        return declined.contains(token.toLowerCase(Locale.ROOT)) ? MatchLevel.MISSING : MatchLevel.UNVERIFIED;
    }

    private static WeaknessStatus statusFor(String token, Set<String> declined) {
        return declined.contains(token.toLowerCase(Locale.ROOT)) ? WeaknessStatus.CONFIRMED_MISSING : WeaknessStatus.NOT_MENTIONED;
    }

    private static String relationFor(String skill, List<String> cvSkills) {
        String lower = skill.toLowerCase(Locale.ROOT);
        List<String> related = SKILL_RELATIONS.getOrDefault(lower, List.of());
        for (String cvSkill : cvSkills) {
            String cvLower = cvSkill.toLowerCase(Locale.ROOT);
            if (related.stream().anyMatch(cvLower::contains) || cvLower.contains(lower)) {
                return "Builds on your " + cvSkill + " experience";
            }
        }
        List<String> words = Stream.of(lower.split("[\\s/]+")).filter(w -> w.length() > 3).toList();
        for (String cvSkill : cvSkills) {
            if (words.stream().anyMatch(w -> cvSkill.toLowerCase(Locale.ROOT).contains(w))) {
                return "Builds on your " + cvSkill + " experience";
            }
        }
        return "Uses the same foundations as your current stack";
    }
}
