package com.backend.backend.service.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.backend.backend.model.AnalysisInsights;
import com.backend.backend.model.CertificationItem;
import com.backend.backend.model.CvDecision;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.DecisionAction;
import com.backend.backend.model.EducationItem;
import com.backend.backend.model.Experience;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.JobRequirements;
import com.backend.backend.model.LanguageItem;
import com.backend.backend.model.MatchLevel;
import com.backend.backend.model.PersonalInfo;
import com.backend.backend.model.Priority;
import com.backend.backend.model.ProjectItem;
import com.backend.backend.model.Relevance;
import com.backend.backend.model.SectionProposal;
import com.backend.backend.model.WeaknessStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class MatchingEnginesTest {

    private final CustomizationEngine engine = new CustomizationEngine();
    private final ScoringEngine scoring = new ScoringEngine();

    public static final CvProfile CV = new CvProfile(
            new PersonalInfo("Raed Zayoud", "raed@example.com", "+216 55 000 000", "Tunis, Tunisia",
                    List.of("github.com/raedzayoud")),
            "Software Engineer",
            "Software engineer with 5 years of experience building web and mobile applications.",
            List.of("Java", "Spring Boot", "Angular", "TypeScript", "Flutter", "PostgreSQL", "REST APIs", "Docker", "Git", "Microservices"),
            List.of(
                    new Experience("exp-1", "Software Engineer", "Novadev", "Tunis", "Mar 2022", "Present", List.of(
                            "Built REST APIs with Spring Boot serving 40k requests per day.",
                            "Led the migration of the frontend to Angular 15, reducing page load time by 30%."), List.of("Spring Boot", "Angular")),
                    new Experience("exp-2", "Backend Developer", "MedTech Solutions", "Tunis", "Jan 2020", "Feb 2022", List.of(
                            "Designed PostgreSQL data models for a clinic management platform."), List.of())),
            List.of(new EducationItem("edu-1", "Engineering degree", "National Engineering School of Tunis", "Tunis", "2015", "2020", "")),
            List.of(
                    new ProjectItem("prj-1", "Clinic Management API", "Backend built with Spring Boot.", List.of("Java", "PostgreSQL"), ""),
                    new ProjectItem("prj-2", "Delivery tracking app", "Flutter app for couriers.", List.of("Flutter", "Dart"), "")),
            List.of(new CertificationItem("cert-1", "AWS Certified Cloud Practitioner", "Amazon Web Services", "2023")),
            List.of(new LanguageItem("lang-1", "English", "Professional")),
            List.of("4th place in a national coding contest (50% of teams) & #1 in C# track"));

    public static final JobPosition FRONTEND = new JobPosition("t", "Frontend Developer", "",
            new JobRequirements(List.of("React", "TypeScript", "CSS"), List.of("Angular"), null, null, List.of(), List.of("Git"), List.of()));

    @Test
    void textMatcherRespectsTokenBoundaries() {
        assertThat(TextMatcher.mentions("built with java and c++", "Java")).isTrue();
        assertThat(TextMatcher.mentions("javascript developer", "Java")).isFalse();
        assertThat(TextMatcher.mentions("c++ expert", "C")).isFalse();
        assertThat(TextMatcher.mentions("apis written in java.", "java")).isTrue();
        assertThat(TextMatcher.mentions("node.js services", "node")).isFalse();
    }

    @Test
    void neverRemovesAnythingBeforeTheUserAsks() {
        List<CvDecision> decisions = engine.buildPlan(CV, FRONTEND);
        assertThat(decisions).noneMatch(d -> d.getAction() == DecisionAction.REMOVE);
        assertThat(decisions).filteredOn(d -> d.getSuggestion() == Relevance.SUGGEST_REMOVE)
                .allMatch(d -> d.getAction() == DecisionAction.DEPRIORITIZE);
    }

    @Test
    void highlightsDirectMatchesAndLowersUnrelatedSkills() {
        List<CvDecision> decisions = engine.buildPlan(CV, FRONTEND);
        assertThat(actionFor(decisions, "TypeScript")).isEqualTo(DecisionAction.HIGHLIGHT);
        assertThat(actionFor(decisions, "Angular")).isEqualTo(DecisionAction.KEEP);
        assertThat(actionFor(decisions, "Flutter")).isEqualTo(DecisionAction.DEPRIORITIZE);
    }

    @Test
    void keepsJobsDatesAndSchoolsAndDropsOnlyApprovedRemovals() {
        List<CvDecision> decisions = engine.buildPlan(CV, FRONTEND);
        decisions.stream().filter(d -> d.getLabel().equals("Java")).forEach(d -> d.setAction(DecisionAction.REMOVE));
        List<SectionProposal> sections = engine.buildSections(CV, FRONTEND, decisions);

        assertThat(proposed(sections, "experience")).contains("Novadev", "MedTech Solutions", "Mar 2022", "Jan 2020");
        assertThat(proposed(sections, "education")).contains("National Engineering School of Tunis");
        assertThat(proposed(sections, "skills")).doesNotContainPattern("\\bJava\\b").contains("Angular");
        assertThat(proposed(sections, "summary")).doesNotContain("React");
        assertThat(sections).filteredOn(SectionProposal::isLocked).extracting(SectionProposal::getKey)
                .containsExactly("education", "personal");
    }

    @Test
    void flagsClaimsTheCvCannotBack() {
        assertThat(engine.violationsFor("Expert in React and Kubernetes.", CV, FRONTEND)).hasSize(2);
        assertThat(engine.violationsFor("Built Angular apps in TypeScript.", CV, FRONTEND)).isEmpty();
    }

    @Test
    void suggestsTransferableSkillsOnlyFromTheCv() {
        assertThat(engine.transferableFor("React", CV)).contains("Angular", "TypeScript").doesNotContain("Vue");
        assertThat(engine.missingSkills(CV, FRONTEND)).containsExactly("React", "CSS");
    }

    @Test
    void mergesMultiRolePostings() {
        JobPosition other = new JobPosition("b", "Java Developer", "",
                new JobRequirements(List.of("Java", "React"), List.of(), 3, "Degree", List.of(), List.of(), List.of()));
        JobPosition merged = engine.mergePositions(List.of(FRONTEND, other));
        assertThat(merged.title()).isEqualTo("Frontend Developer +1 more roles");
        assertThat(merged.requirements().technical()).containsExactly("React", "TypeScript", "CSS", "Java");
        assertThat(merged.requirements().yearsExperience()).isEqualTo(3);
    }

    @Test
    void scoringIsDeterministicAndPointsSumToScore() {
        AnalysisInsights first = scoring.compute(CV, FRONTEND, "a1", Set.of());
        assertThat(first).isEqualTo(scoring.compute(CV, FRONTEND, "a1", Set.of()));
        assertThat(first.criteria().stream().mapToInt(AnalysisInsights.ScoreCriterion::points).sum()).isEqualTo(first.score());
        assertThat(first.score()).isBetween(0, 100);
        assertThat(first.disclaimer()).isEqualTo(ScoringEngine.MATCH_DISCLAIMER);
    }

    @Test
    void declinedSkillsBecomeConfirmedMissing() {
        AnalysisInsights open = scoring.compute(CV, FRONTEND, "a1", Set.of());
        AnalysisInsights declined = scoring.compute(CV, FRONTEND, "a1", Set.of("react"));
        assertThat(level(open, "React")).isEqualTo(MatchLevel.UNVERIFIED);
        assertThat(level(declined, "React")).isEqualTo(MatchLevel.MISSING);
        assertThat(declined.weaknesses()).filteredOn(w -> w.id().equals("weak:tech:react"))
                .extracting(AnalysisInsights.Weakness::status).containsExactly(WeaknessStatus.CONFIRMED_MISSING);
    }

    @Test
    void strongMatchScoresHighWithNoSkillGaps() {
        JobPosition backend = new JobPosition("j", "Backend Developer", "",
                new JobRequirements(List.of("Java", "Spring Boot"), List.of("Docker"), null, null, List.of(), List.of("PostgreSQL"), List.of()));
        AnalysisInsights result = scoring.compute(CV, backend, "a2", Set.of());
        assertThat(result.criteria().getFirst().ratio()).isEqualTo(1.0);
        assertThat(result.score()).isGreaterThanOrEqualTo(75);
        assertThat(result.skillRecommendations()).isEmpty();
    }

    @Test
    void recommendsOnlyMissingSkillsWithPriority() {
        AnalysisInsights result = scoring.compute(CV, FRONTEND, "a1", Set.of());
        assertThat(result.skillRecommendations()).extracting(AnalysisInsights.SkillRecommendation::skill).containsExactly("React", "CSS");
        assertThat(result.skillRecommendations().getFirst().priority()).isEqualTo(Priority.HIGH);
    }

    private static DecisionAction actionFor(List<CvDecision> decisions, String label) {
        return decisions.stream().filter(d -> d.getLabel().equals(label)).findFirst().orElseThrow().getAction();
    }

    private static String proposed(List<SectionProposal> sections, String key) {
        return sections.stream().filter(s -> s.getKey().equals(key)).findFirst().orElseThrow().getProposed();
    }

    private static MatchLevel level(AnalysisInsights insights, String requirement) {
        return insights.criteria().getFirst().evidence().stream()
                .filter(e -> e.requirement().equals(requirement)).findFirst().orElseThrow().level();
    }
}
