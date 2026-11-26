package com.backend.backend.service.ai;

import com.backend.backend.config.AppProperties;
import com.backend.backend.integration.ai.AiChatClient;
import com.backend.backend.integration.ai.AiException;
import com.backend.backend.integration.ai.AiRequest;
import com.backend.backend.integration.ai.AiRequest.Message;
import com.backend.backend.model.JobPosition;
import com.backend.backend.model.JobRequirements;
import com.backend.backend.service.matching.SkillDictionary;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Reads a job description (single role or a multi-role announcement) into positions with
 * requirements. Falls back to keyword detection when the AI is unavailable, so jobs can always be added.
 */
@Slf4j
@Service
public class JobParsingService {

    private static final int MAX_POSITIONS = 15;

    private static final String SYSTEM_PROMPT = """
            You read job postings and extract hiring requirements. A posting may describe one role or list
            several roles (a recruitment announcement). Rules:
            - One entry in "positions" per distinct role, in the order they appear (at most 15).
            - Keep role titles exactly as written, in the posting's language.
            - "technical": required hard skills/technologies, short canonical names ("Java", "Spring Boot", "SQL").
            - "optional": skills described as a plus/nice to have.
            - "tools": named tools/platforms (e.g. "Git", "Docker", "Jira") the posting asks for.
            - "softSkills": interpersonal skills the posting names.
            - "responsibilities": short phrases of what the person will do.
            - "yearsExperience": the minimum number of years required, or null. "education": the required degree or null.
            - If a role is only listed by title with no details, fill "technical" with the 3 to 6 most standard
              core requirements for that exact title and leave the other lists empty.
            - Never copy requirements from one role into another unless the posting says they are shared.
            Return ONLY a JSON object:
            {"positions":[{"title":"","summary":"one sentence","requirements":{"technical":[],"optional":[],
            "yearsExperience":null,"education":null,"responsibilities":[],"tools":[],"softSkills":[]}}]}
            """;

    private final AiChatClient ai;
    private final AiJson aiJson;
    private final int maxInputChars;

    public JobParsingService(AiChatClient ai, AiJson aiJson, AppProperties properties) {
        this.ai = ai;
        this.aiJson = aiJson;
        this.maxInputChars = properties.ai().maxInputChars();
    }

    public List<JobPosition> parse(String text) {
        if (ai.isConfigured()) {
            try {
                String content = ai.complete(new AiRequest("job-parsing",
                        List.of(Message.system(SYSTEM_PROMPT), Message.user("Job posting:\n\"\"\"\n" + AiJson.clip(text, maxInputChars) + "\n\"\"\"")),
                        true, 0.1, 8000));
                List<JobPosition> positions = toPositions(aiJson.parseObject(content).path("positions"));
                if (!positions.isEmpty()) return positions;
                log.warn("Job parsing returned no positions; using keyword fallback");
            } catch (AiException e) {
                // Budget exhaustion must surface: silently degrading would hide abuse. Other failures degrade to keywords.
                if (e.getKind() == AiException.Kind.BUDGET_EXHAUSTED) throw e;
                log.warn("Job parsing fell back to keyword detection: {}", e.getKind());
            }
        }
        return List.of(fallback(text));
    }

    private List<JobPosition> toPositions(JsonNode array) {
        List<JobPosition> positions = new ArrayList<>();
        if (!array.isArray()) return positions;
        for (JsonNode node : array) {
            if (positions.size() >= MAX_POSITIONS) break;
            JobPosition parsed = aiJson.convert(node, JobPosition.class);
            positions.add(new JobPosition("pos-" + (positions.size() + 1), parsed.title(), parsed.summary(), parsed.requirements()));
        }
        return positions;
    }

    /** Title from the first line, requirements from known technology names in the text. */
    static JobPosition fallback(String text) {
        String title = text.lines().map(String::trim).filter(l -> !l.isEmpty()).findFirst().orElse("Job posting");
        if (title.length() > 120) title = title.substring(0, 117) + "...";
        List<String> skills = SkillDictionary.findIn(text);
        return new JobPosition("pos-1", title, "Requirements detected from keywords in the posting.",
                new JobRequirements(skills, List.of(), null, null, List.of(), List.of(), List.of()));
    }
}
