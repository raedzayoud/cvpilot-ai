package com.backend.backend.service.agent;

import static com.backend.backend.service.agent.AgentTestSupport.CV;
import static com.backend.backend.service.agent.AgentTestSupport.call;
import static com.backend.backend.service.agent.AgentTestSupport.orchestrator;
import static com.backend.backend.service.agent.AgentTestSupport.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backend.backend.model.CvProfile;
import com.backend.backend.model.CvSections;
import com.backend.backend.model.Experience;
import com.backend.backend.model.ProjectItem;
import com.backend.backend.model.agent.AgentFacts.Source;
import com.backend.backend.model.agent.AgentIntent;
import com.backend.backend.model.agent.AgentOperation;
import com.backend.backend.model.agent.ProposedChange;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CvAgentTest {

    private static final String DOTNET_JOB = "We are looking for a .NET Backend Developer. Requirements: C#, .NET, SQL Server, "
            + "REST APIs, Git. Nice to have: Docker. Responsibilities: you will design backend services and APIs for our "
            + "platform, review code and mentor juniors. 3+ years of experience required.";

    @Test
    void tailoringToAJobNeverClaimsMissingSkills() {
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("analyze_job_description", Map.of("job_description", DOTNET_JOB)))
                // The model tries to claim .NET: the tool must reject it.
                .then("", call("rewrite_summary", Map.of("summary",
                        "Backend engineer experienced in C# and .NET building REST APIs.", "reason", "Match the job")))
                // Then it emphasizes real backend experience instead.
                .then("", call("rewrite_summary", Map.of("summary",
                        "Backend-focused software engineer building REST APIs with Java and Spring Boot on PostgreSQL.",
                        "reason", "Emphasize backend work the job values")))
                .then("I emphasized your backend experience. I found no confirmed .NET or C# experience in your CV.");
        AgentState state = state("Adapt my CV for this job: " + DOTNET_JOB);

        var outcome = orchestrator(ai).run(state, "English", List.of(), state.latestUserMessage(), List.of());

        // (The fixture's achievements mention a "C# track", so C# itself counts as being in the CV.)
        assertThat(state.getJobTarget().missingSkills()).contains(".NET", "SQL Server");
        assertThat(state.getJobTarget().matchedSkills()).contains("REST APIs", "Git", "Docker");
        assertThat(state.getBlockedClaims()).anyMatch(p -> p.contains(".NET"));
        assertThat(outcome.proposals()).singleElement().satisfies(p -> {
            assertThat(p.operation()).isEqualTo(AgentOperation.UPDATE_SUMMARY);
            assertThat(p.updatedText()).doesNotContain(".NET", "C#");
            assertThat(p.jobRequirements()).contains("REST APIs");
            assertThat(p.evidence()).allMatch(e -> e.source() == Source.CV);
        });
        assertThat(outcome.reply()).contains("no confirmed .NET");
        // The agent saw the tool results (rejection) before answering.
        assertThat(ai.requests.getLast().messages().toString()).contains("rejected");
    }

    @Test
    void aSkillStatedByTheUserCanBeAddedAfterItIsRecorded() {
        String said = "I built a REST API with ASP.NET Core and SQL Server for my internship.";
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("record_user_fact", Map.of("quote", "I built a REST API with ASP.NET Core and SQL Server",
                        "skills", List.of("ASP.NET Core", "SQL Server"))))
                .then("", call("set_skills", Map.of("skills", List.of("Java", "Spring Boot", "ASP.NET Core", "SQL Server"),
                        "reason", "Add the skills you confirmed")));
        AgentState state = state(said);

        var outcome = orchestrator(ai).run(state, "English", List.of(), said, List.of());

        assertThat(state.getFacts()).hasSize(1);
        ProposedChange skills = outcome.proposals().getFirst();
        assertThat(skills.keywordsAdded()).contains("ASP.NET Core", "SQL Server");
        assertThat(skills.evidence()).anyMatch(e -> e.term().equals("SQL Server") && e.source() == Source.USER_CONFIRMED);
        assertThat(skills.keywordsRemoved()).contains("Angular", "Flutter");
    }

    @Test
    void aJobRequirementCannotBeRecordedAsAUserFact() {
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("record_user_fact", Map.of("quote", "Requirements: C#, .NET, SQL Server", "skills", List.of("C#"))));
        AgentState state = state(DOTNET_JOB);

        orchestrator(ai).run(state, "English", List.of(), DOTNET_JOB, List.of());

        assertThat(state.getFacts()).isEmpty();
        assertThat(ai.requests.getLast().messages().toString()).contains("not something the user said about themselves");
    }

    @Test
    void addingAnExperienceRequiresDetailsFromTheUser() {
        String vague = "Add a new Software Engineer experience at Company X.";
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("add_experience", Map.of("role", "Software Engineer", "company", "Company X",
                        "start_date", "January 2025", "bullets", List.of("Led a team of 8 engineers delivering cloud services"),
                        "reason", "User asked")));
        AgentState state = state(vague);

        var outcome = orchestrator(ai).run(state, "English", List.of(), vague, List.of());

        assertThat(outcome.proposals()).isEmpty();
        assertThat(state.getBlockedClaims()).anyMatch(p -> p.contains("January 2025")).anyMatch(p -> p.contains("8"));

        String detailed = "I worked as Software Engineer at Company X from January 2025 to September 2025. "
                + "I built REST APIs with Spring Boot and migrated reports to PostgreSQL.";
        var ai2 = new AgentTestSupport.ScriptedAi()
                .then("", call("add_experience", Map.of("role", "Software Engineer", "company", "Company X",
                        "start_date", "January 2025", "end_date", "September 2025",
                        "bullets", List.of("Built REST APIs with Spring Boot.", "Migrated reports to PostgreSQL."),
                        "technologies", List.of("Spring Boot", "PostgreSQL"), "reason", "User described this role")));
        AgentState state2 = state(vague, detailed);

        var outcome2 = orchestrator(ai2).run(state2, "English", List.of(), detailed, List.of());

        assertThat(outcome2.proposals()).singleElement()
                .satisfies(p -> assertThat(p.experience().company()).isEqualTo("Company X"));
    }

    @Test
    void whatTheUserStatesIsAppliedWithoutAskingForQuotes() {
        // The user's own description, without "I" sentences: it still counts as confirmed by the user.
        String described = """
                Project: E-commerce Web Application
                - Role: Full Stack Developer Intern
                - Company: MajraDEEP
                - Dates: February 2026 – August 2026
                - Implemented RESTful APIs using C# and ASP.NET Core.
                - Built responsive user interfaces using React.js.
                - Designed and managed relational databases using SQL Server and Entity Framework Core.
                Technologies: C#, ASP.NET Core, Entity Framework Core, React.js, SQL Server, REST APIs, Git, Docker.""";
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("add_experience", Map.of("role", "Full Stack Developer Intern", "company", "MajraDEEP",
                        "start_date", "February 2026", "end_date", "August 2026",
                        "bullets", List.of("Implemented RESTful APIs using C# and ASP.NET Core.",
                                "Built responsive user interfaces using React.js.",
                                "Designed and managed relational databases using SQL Server and Entity Framework Core."),
                        "technologies", List.of("C#", "ASP.NET Core", "React.js", "SQL Server"), "reason", "User described this role")),
                        call("set_skills", Map.of("skills", List.of("C#", "ASP.NET Core", "SQL Server", "Java", "Spring Boot"),
                                "reason", "Add the skills you described")));
        AgentState state = state(described);

        var outcome = orchestrator(ai).run(state, "English", List.of(), described, List.of());

        assertThat(state.getBlockedClaims()).isEmpty();
        assertThat(outcome.proposals()).hasSize(2);
        assertThat(outcome.proposals().getLast().evidence())
                .anyMatch(e -> e.term().equals("ASP.NET Core") && e.source() == Source.USER_CONFIRMED);
    }

    @Test
    void anExplicitInstructionToReplaceATechnologyIsExecuted() {
        String instruction = "Replace Spring Boot with .NET in my Novadev experience.";
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("update_experience", Map.of("experience_id", "exp-1",
                        "bullets", List.of("Built REST APIs with .NET serving 40k requests per day.",
                                "Led the migration of the frontend to Angular 15, reducing page load time by 30%."),
                        "technologies", List.of(".NET", "Angular"), "reason", "As you asked")));
        AgentState state = state(instruction);

        var outcome = orchestrator(ai).run(state, "English", List.of(), instruction, List.of());

        assertThat(outcome.proposals()).singleElement()
                .satisfies(p -> assertThat(p.experience().technologies()).containsExactly(".NET", "Angular"));
    }

    @Test
    void aTailoringRequestDoesNotConfirmTheTargetSkill() {
        String request = "Tailor my CV for a .NET Developer position.";
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("rewrite_summary", Map.of("summary", "Experienced .NET developer building REST APIs.", "reason", "x")));
        AgentState state = state(request);

        var outcome = orchestrator(ai).run(state, "English", List.of(), request, List.of());

        assertThat(outcome.proposals()).isEmpty();
        assertThat(state.getBlockedClaims()).anyMatch(p -> p.contains(".NET"));
    }

    @Test
    void removingTheTechnicalSkillsSectionLeavesAnEmptyListNotAPlaceholder() {
        String request = "Remove the entire Technical Skills section.";
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("remove_section", Map.of("section", "skills", "reason", "You asked to remove it")));
        AgentState state = state(request);

        var outcome = orchestrator(ai).run(state, "English", List.of(), request, List.of());

        ProposedChange removal = outcome.proposals().getFirst();
        assertThat(removal.operation()).isEqualTo(AgentOperation.REMOVE_SECTION);
        assertThat(removal.updatedText()).isEmpty();
        assertThat(removal.keywordsRemoved()).contains("Java", "Angular");
        CvProfile after = new ChangeApplier().apply(CV, CvSections.DEFAULT_ORDER, removal).profile();
        assertThat(after.skills()).isEmpty();
        assertThat(after.experiences()).isEqualTo(CV.experiences());
    }

    @Test
    void removingTheTechnologiesIsTreatedAsTheSkillsSection() {
        String request = "Clear the technologies.";
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("remove_section", Map.of("section", "technologies", "reason", "You asked to remove it")));
        AgentState state = state(request);

        var outcome = orchestrator(ai).run(state, "English", List.of(), request, List.of());

        assertThat(outcome.proposals()).singleElement()
                .satisfies(p -> assertThat(p.operation()).isEqualTo(AgentOperation.REMOVE_SECTION));
    }

    @Test
    void removingTheTechnologiesIsHandledWithoutTheModel() {
        String request = "remove the technologies";
        var ai = new AgentTestSupport.ScriptedAi();
        AgentState state = state(request);

        var outcome = orchestrator(ai).run(state, "English", List.of(), request, List.of());

        assertThat(ai.requests).isEmpty();
        assertThat(outcome.proposals()).singleElement()
                .satisfies(p -> assertThat(p.operation()).isEqualTo(AgentOperation.REMOVE_SECTION));
    }

    @Test
    void removingTechnologiesFromACvThatHasNoneExplainsItWithoutTheModel() {
        String request = "remove the technologies";
        CvProfile noSkills = new CvProfile(CV.personalInfo(), CV.professionalTitle(), CV.summary(), List.of(),
                CV.experiences(), CV.education(), CV.projects(), CV.certifications(), CV.languages(), CV.achievements());
        AgentState state = new AgentState(noSkills, CvSections.DEFAULT_ORDER, List.of(), null, List.of(request),
                List.of(request), Map.of(), AgentIntent.REMOVE_CONTENT);
        var ai = new AgentTestSupport.ScriptedAi();

        var outcome = orchestrator(ai).run(state, "English", List.of(), request, List.of());

        assertThat(ai.requests).isEmpty();
        assertThat(outcome.proposals()).isEmpty();
        assertThat(outcome.reply()).contains("already empty");
    }

    @Test
    void anEmptySkillsListIsAcceptedAsRemovingTheSection() {
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("set_skills", Map.of("skills", List.of(), "reason", "Remove all skills")),
                        call("remove_section", Map.of("section", "experience", "reason", "x")));
        AgentState state = state("Clear all my skills");

        var outcome = orchestrator(ai).run(state, "English", List.of(), state.latestUserMessage(), List.of());

        assertThat(outcome.proposals()).singleElement()
                .satisfies(p -> assertThat(p.operation()).isEqualTo(AgentOperation.REMOVE_SECTION));
        // Whole core sections are not removable; individual items are.
        assertThat(ai.requests.getLast().messages().toString()).contains("cannot be removed as a whole");
    }

    @Test
    void factsOfAnExistingExperienceCannotBeChangedSilently() {
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("update_experience", Map.of("experience_id", "exp-1", "company", "Google",
                        "bullets", List.of("Built REST APIs with Spring Boot serving 40k requests per day."), "reason", "x")));
        AgentState state = state("Rewrite my first experience with action verbs.");

        var outcome = orchestrator(ai).run(state, "English", List.of(), state.latestUserMessage(), List.of());

        assertThat(outcome.proposals()).isEmpty();
        assertThat(state.getBlockedClaims()).anyMatch(p -> p.contains("Google"));
    }

    @Test
    void acceptedChangesApplyAndOldProposalsBecomeStale() {
        ChangeApplier applier = new ChangeApplier();
        ProposedChange summary = new ProposedChange(AgentOperation.UPDATE_SUMMARY, "summary", null, "Summary",
                CV.summary(), "New summary.", "r", null, null, null, null, "New summary.", null, null, null,
                CV.summary(), null, null, null);
        var applied = applier.apply(CV, CvSections.DEFAULT_ORDER, summary);
        assertThat(applied.profile().summary()).isEqualTo("New summary.");

        // Applying the same (now outdated) proposal again must not overwrite the newer text.
        assertThatThrownBy(() -> applier.apply(applied.profile(), CvSections.DEFAULT_ORDER, summary))
                .isInstanceOf(ChangeApplier.StaleException.class);

        ProposedChange order = new ProposedChange(AgentOperation.REORDER_SECTIONS, "layout", null, "Order", "", "", "r",
                null, null, null, null, null, List.of("experience", "skills"), null, null, null, CvSections.DEFAULT_ORDER, null, null);
        assertThat(applier.apply(CV, CvSections.DEFAULT_ORDER, order).sectionOrder())
                .startsWith("experience", "skills").hasSize(CvSections.DEFAULT_ORDER.size());
    }

    @Test
    void removalTargetsTheRightItem() {
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("remove_project", Map.of("project_id", "prj-2", "reason", "Not relevant to backend roles")));
        AgentState state = state("Remove irrelevant projects");

        var outcome = orchestrator(ai).run(state, "English", List.of(), state.latestUserMessage(), List.of());
        ProposedChange removal = outcome.proposals().getFirst();
        CvProfile after = new ChangeApplier().apply(CV, CvSections.DEFAULT_ORDER, removal).profile();

        assertThat(after.projects()).extracting(p -> p.name()).containsExactly("Clinic Management API");
    }

    @Test
    void replacingATechnologyEverywhereIsHandledWithoutTheModel() {
        String request = "replace all the technologies in my cv from spring boot to .net";
        var ai = new AgentTestSupport.ScriptedAi();
        AgentState state = state(request);

        var outcome = orchestrator(ai).run(state, "English", List.of(), request, List.of());

        assertThat(ai.requests).isEmpty();
        ProposedChange change = outcome.proposals().getFirst();
        assertThat(change.operation()).isEqualTo(AgentOperation.REPLACE_TECHNOLOGY);
        assertThat(change.originalText()).isEqualTo("Spring Boot");
        assertThat(change.updatedText()).isEqualTo(".NET");

        CvProfile after = new ChangeApplier().apply(CV, CvSections.DEFAULT_ORDER, change).profile();
        assertThat(after.skills()).contains(".NET").doesNotContain("Spring Boot");
        assertThat(after.experiences()).flatExtracting(Experience::technologies).doesNotContain("Spring Boot");
        assertThat(after.experiences().getFirst().bullets().getFirst()).contains(".NET");
        assertThat(after.projects()).filteredOn(pr -> pr.id().equals("prj-1"))
                .singleElement().satisfies(pr -> assertThat(pr.description()).isEqualTo("Backend built with .NET."));
        assertThat(after.summary()).isEqualTo(CV.summary());
    }

    @Test
    void replacingATechnologyThatIsNotInTheCvExplainsItWithoutTheModel() {
        String request = "replace Spring Boot with .NET";
        CvProfile noSpringBoot = ChangeApplier.replaceTechnology(CV, "Spring Boot", ".NET");
        AgentState state = new AgentState(noSpringBoot, CvSections.DEFAULT_ORDER, List.of(), null, List.of(request),
                List.of(request), Map.of(), AgentIntent.REWRITE);
        var ai = new AgentTestSupport.ScriptedAi();

        var outcome = orchestrator(ai).run(state, "English", List.of(), request, List.of());

        assertThat(ai.requests).isEmpty();
        assertThat(outcome.proposals()).isEmpty();
        assertThat(outcome.reply()).contains("No 'Spring Boot'");
    }

    @Test
    void changingOnlyTheTechnologiesKeepsTheExistingBullets() {
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("update_experience", Map.of("experience_id", "exp-1",
                        "technologies", List.of(".NET", "Angular"), "reason", "Swap the technologies")));
        AgentState state = state("Change the technologies in exp-1 to .NET and Angular.");

        var outcome = orchestrator(ai).run(state, "English", List.of(), state.latestUserMessage(), List.of());

        assertThat(outcome.proposals()).singleElement().satisfies(p -> {
            assertThat(p.experience().technologies()).containsExactly(".NET", "Angular");
            assertThat(p.experience().bullets()).isEqualTo(CV.experiences().getFirst().bullets());
        });
    }

    @Test
    void changingOnlyTheTechnologiesOfAProjectKeepsItsDescription() {
        var ai = new AgentTestSupport.ScriptedAi()
                .then("", call("update_project", Map.of("project_id", "prj-1",
                        "technologies", List.of(".NET"), "reason", "Swap the technologies")));
        AgentState state = state("Change the technologies of prj-1 to .NET.");

        var outcome = orchestrator(ai).run(state, "English", List.of(), state.latestUserMessage(), List.of());

        assertThat(outcome.proposals()).singleElement().satisfies(p -> {
            assertThat(p.project().technologies()).containsExactly(".NET");
            assertThat(p.project().description()).isEqualTo(CV.projects().getFirst().description());
        });
    }

    @Test
    void intentsAreDetected() {
        IntentClassifier classifier = new IntentClassifier();
        assertThat(classifier.classify("Optimize my CV for ATS systems")).isEqualTo(AgentIntent.OPTIMIZE_ATS);
        assertThat(classifier.classify("Tailor my CV for this .NET Developer position")).isEqualTo(AgentIntent.TAILOR_TO_JOB);
        assertThat(classifier.classify("Add a project using Spring Boot and Angular")).isEqualTo(AgentIntent.ADD_CONTENT);
        assertThat(classifier.classify("Remove irrelevant information")).isEqualTo(AgentIntent.REMOVE_CONTENT);
        assertThat(classifier.classify("Rewrite my professional summary")).isEqualTo(AgentIntent.REWRITE);
        assertThat(classifier.classify(DOTNET_JOB)).isEqualTo(AgentIntent.TAILOR_TO_JOB);
    }
}
