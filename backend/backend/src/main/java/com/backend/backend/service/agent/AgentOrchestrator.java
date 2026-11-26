package com.backend.backend.service.agent;

import com.backend.backend.integration.ai.AiChatClient;
import com.backend.backend.integration.ai.AiToolRequest;
import com.backend.backend.integration.ai.AiToolTurn;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.agent.AgentIntent;
import com.backend.backend.model.agent.ProposedChange;
import com.backend.backend.service.matching.SkillDictionary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * The agent loop: plan → call tools → observe results → answer. Bounded to a few steps per turn so a
 * confused model cannot loop or burn the AI budget. Holds no state of its own between turns.
 */
@Slf4j
@Service
public class AgentOrchestrator {

    static final int MAX_STEPS = 4;
    private static final java.util.Set<AgentIntent> ACTIONABLE = java.util.EnumSet.of(
            AgentIntent.TAILOR_TO_JOB, AgentIntent.OPTIMIZE_ATS, AgentIntent.REWRITE, AgentIntent.REMOVE_CONTENT, AgentIntent.REORDER,
            AgentIntent.ADD_CONTENT);
    private static final int HISTORY = 6;

    /**
     * "Remove the technical skills/technologies" is unambiguous and destructive: handle it without the model,
     * so it always becomes a proposal (or a plain explanation when the section is already empty) and never
     * fails on a slow/unavailable model.
     */
    private static final java.util.regex.Pattern REMOVE_SKILLS = java.util.regex.Pattern.compile(
            "\\b(remove|delete|drop|get rid of|supprim\\w*|enl[eè]ve\\w*|retir\\w*)\\b[^\\n.]{0,60}?"
                    + "\\b(technical\\s+skills|tech\\s+skills|skills|technologies)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    /** A global technology swap ("replace all Spring Boot with .NET") must not depend on the model either. */
    private static final java.util.regex.Pattern REPLACE = java.util.regex.Pattern.compile(
            "\\b(?:replace|swap|change|remplace|remplacer)\\b(.*?)\\b(?:with|to|by|par|vers)\\b(.*)",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.DOTALL);
    private static final java.util.regex.Pattern REPLACE_FROM = java.util.regex.Pattern.compile(
            "\\b(?:from|de|du)\\b(.*)$",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE | java.util.regex.Pattern.DOTALL);
    /** Scoped to one item: let the model edit that experience/project instead of swapping everywhere. */
    private static final java.util.regex.Pattern REPLACE_SCOPED = java.util.regex.Pattern.compile(
            "\\b(experience|experiences|project|projects|company|role|internship|position)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    public record HistoryItem(boolean fromUser, String text) {
    }

    public record Outcome(String reply, List<ProposedChange> proposals) {
    }

    private final AiChatClient ai;
    private final AgentToolbox toolbox;
    private final IntentClassifier intents;
    private final JsonMapper json;

    public AgentOrchestrator(AiChatClient ai, AgentToolbox toolbox, IntentClassifier intents, JsonMapper json) {
        this.ai = ai;
        this.toolbox = toolbox;
        this.intents = intents;
        this.json = json;
    }

    public boolean available() {
        return ai.isConfigured();
    }

    public Outcome run(AgentState state, String language, List<HistoryItem> history, String userText,
                       List<Map<String, String>> pendingProposals) {
        if (state.getIntent() == AgentIntent.REMOVE_CONTENT && REMOVE_SKILLS.matcher(userText).find()) {
            return removeSkills(state);
        }
        if (!REPLACE_SCOPED.matcher(userText).find()) {
            Outcome replaced = replaceTechnology(state, userText);
            if (replaced != null) return replaced;
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", AgentPrompts.SYSTEM.formatted(language)));
        messages.add(message("system", "CONTEXT (JSON):\n" + json.writeValueAsString(context(state, pendingProposals))));
        int from = Math.max(0, history.size() - HISTORY);
        for (HistoryItem item : history.subList(from, history.size())) {
            messages.add(message(item.fromUser() ? "user" : "assistant", item.text()));
        }
        messages.add(message("user", userText));

        // Planning step: a pasted job description is analyzed before the model plans its edits, so tailoring
        // always starts from the matched/missing requirements rather than depending on the model to ask for it.
        if (state.getIntent() == AgentIntent.TAILOR_TO_JOB && intents.looksLikeJobDescription(userText)) {
            String args = json.writeValueAsString(Map.of("job_description", userText));
            executeTools(messages, List.of(new AiToolTurn.Call("plan-analyze-job", "analyze_job_description", args)), state, null);
        }

        String reply = "";
        boolean nudged = false;
        for (int step = 0; step < MAX_STEPS; step++) {
            AiToolTurn turn = step(messages, state);
            if (turn == null) break; // the model failed twice; keep what was already proposed
            if (!turn.wantsTools()) {
                // An actionable request answered with words only: remind the model once to make the supported edits.
                if (!nudged && state.getProposals().isEmpty() && ACTIONABLE.contains(state.getIntent()) && step < MAX_STEPS - 1) {
                    nudged = true;
                    messages.add(message("assistant", turn.content()));
                    messages.add(message("system", "You have not proposed any change yet. Call the editing tools now for every change the user asked for and every "
                            + "improvement the CV already supports (summary, headline, skills order, relevant experiences and "
                            + "projects). Then give your final reply, including your questions about missing information."));
                    continue;
                }
                reply = turn.content();
                break;
            }
            executeTools(messages, turn.toolCalls(), state, turn.content());
        }
        List<ProposedChange> proposals = List.copyOf(state.getProposals().values());
        if (reply == null || reply.isBlank()) reply = fallbackReply(state, proposals);
        log.info("Agent turn: intent={}, tools={}, proposals={}, blocked={}", state.getIntent(), state.getToolsUsed(),
                proposals.size(), state.getBlockedClaims().size());
        return new Outcome(reply.strip(), proposals);
    }

    private Outcome removeSkills(AgentState state) {
        Map<String, Object> result = toolbox.execute("remove_section", json.writeValueAsString(Map.of(
                "section", "skills", "reason", "You asked to remove the technical skills.")), state);
        String reply = switch (String.valueOf(result.get("status"))) {
            case "proposed" -> "I've prepared a change to remove the Technical Skills section. Review it in the Changes panel.";
            case "error" -> String.valueOf(result.get("message"));
            default -> "The Technical Skills section is already empty, so there is nothing to remove.";
        };
        List<ProposedChange> proposals = List.copyOf(state.getProposals().values());
        log.info("Agent turn: intent=REMOVE_CONTENT, direct skills removal, proposals={}", proposals.size());
        return new Outcome(reply.strip(), proposals);
    }

    /**
     * Deterministic global swap. Returns null unless the text is a clear "replace X with Y" and X is a technology
     * we know, so anything ambiguous still goes to the model.
     */
    private Outcome replaceTechnology(AgentState state, String userText) {
        java.util.regex.Matcher m = REPLACE.matcher(userText);
        if (!m.find()) return null;
        java.util.regex.Matcher from = REPLACE_FROM.matcher(m.group(1));
        String source = longestKnown(from.find() ? from.group(1) : m.group(1));
        if (source == null) return null;
        String target = replacement(m.group(2));
        if (target.isBlank() || source.equalsIgnoreCase(target)) return null;
        Map<String, Object> result = toolbox.execute("replace_technology", json.writeValueAsString(Map.of(
                "from", source, "to", target, "reason", "You asked to replace " + source + " with " + target + ".")), state);
        String reply = switch (String.valueOf(result.get("status"))) {
            case "proposed" -> "I've prepared a change to replace " + source + " with " + target
                    + " everywhere. Review it in the Changes panel.";
            case "error" -> String.valueOf(result.get("message"));
            default -> "There was nothing to replace in your CV.";
        };
        log.info("Agent turn: direct technology replace {} -> {}, proposals={}", source, target,
                state.getProposals().size());
        return new Outcome(reply.strip(), List.copyOf(state.getProposals().values()));
    }

    private static String replacement(String text) {
        String known = longestKnown(text);
        if (known != null) return known;
        String stripped = text.strip().replaceFirst("(?i)^(use|using|into)\\s+", "");
        if (stripped.isEmpty()) return "";
        String first = stripped.split("\\s+", 2)[0].replaceAll("[.,;:!?)\\]}]+$", "");
        return first.length() < 2 ? "" : first;
    }

    private static String longestKnown(String text) {
        return SkillDictionary.findIn(text).stream().max(Comparator.comparingInt(String::length)).orElse(null);
    }

    /**
     * One model step. A malformed generation (e.g. a misspelled tool name, which the provider rejects) is
     * retried once; if it fails again, returns null when earlier steps already produced proposals, so the
     * user keeps them, and otherwise reports the failure.
     */
    private AiToolTurn step(List<Map<String, Object>> messages, AgentState state) {
        // Free-tier TPM (8K/min for gpt-oss) is the real limit: a big completion cap and long steps get 429s.
        AiToolRequest request = new AiToolRequest("cv-agent", messages, toolbox.definitions(), 0.3, 2000, false);
        try {
            return ai.chat(request);
        } catch (com.backend.backend.integration.ai.AiException e) {
            if (e.getKind() != com.backend.backend.integration.ai.AiException.Kind.BAD_RESPONSE) throw e;
            log.info("Agent step produced an invalid generation; retrying once");
            try {
                return ai.chat(request);
            } catch (com.backend.backend.integration.ai.AiException again) {
                if (again.getKind() == com.backend.backend.integration.ai.AiException.Kind.BAD_RESPONSE && !state.getProposals().isEmpty()) {
                    return null;
                }
                throw again;
            }
        }
    }

    /** Records the assistant's tool calls and the tool results in the conversation (OpenAI tool-call format). */
    private void executeTools(List<Map<String, Object>> messages, List<AiToolTurn.Call> calls, AgentState state, String content) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", content == null || content.isBlank() ? null : content);
        assistant.put("tool_calls", calls.stream().map(c -> Map.of("id", c.id(), "type", "function",
                "function", Map.of("name", c.name(), "arguments", c.argumentsJson()))).toList());
        messages.add(assistant);
        for (AiToolTurn.Call call : calls) {
            Map<String, Object> result = toolbox.execute(call.name(), call.argumentsJson(), state);
            Map<String, Object> toolMessage = new LinkedHashMap<>();
            toolMessage.put("role", "tool");
            toolMessage.put("tool_call_id", call.id());
            toolMessage.put("content", json.writeValueAsString(result));
            messages.add(toolMessage);
        }
    }

    /** The CV and session facts the model works from (context management). */
    /**
     * Compact view of the CV: everything needed to edit it, nothing empty. Kept small on purpose; providers
     * meter tokens per minute and the context is sent at every step.
     */
    static Map<String, Object> context(AgentState state, List<Map<String, String>> pendingProposals) {
        CvProfile p = state.getProfile();
        Map<String, Object> cv = new LinkedHashMap<>();
        put(cv, "headline", p.professionalTitle());
        put(cv, "summary", p.summary());
        put(cv, "skills", p.skills());
        put(cv, "experiences", p.experiences().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("role", e.role());
            m.put("company", e.company());
            put(m, "location", e.location());
            put(m, "dates", ChangeDescriber.join(" – ", e.start(), e.end()));
            put(m, "bullets", e.bullets());
            put(m, "tech", e.technologies());
            return m;
        }).toList());
        put(cv, "projects", p.projects().stream().map(pr -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pr.id());
            m.put("name", pr.name());
            put(m, "description", pr.description());
            put(m, "tech", pr.technologies());
            return m;
        }).toList());
        put(cv, "education", p.education().stream().map(e -> ChangeDescriber.join(", ", e.degree(), e.school(),
                ChangeDescriber.join(" – ", e.start(), e.end()), e.details())).toList());
        put(cv, "certifications", p.certifications().stream().map(c -> ChangeDescriber.join(", ", c.name(), c.issuer(), c.year())).toList());
        put(cv, "languages", p.languages().stream().map(l -> ChangeDescriber.join(" ", l.name(), l.level())).toList());
        put(cv, "achievements", p.achievements());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("cv", cv);
        context.put("section_order", state.getSectionOrder());
        put(context, "facts_confirmed_by_user", state.getFacts().stream().map(f -> f.quote()).toList());
        if (state.getJobTarget() != null) {
            var job = state.getJobTarget();
            context.put("job_target", Map.of("title", job.title(), "matched_skills", job.matchedSkills(),
                    "missing_skills", job.missingSkills(), "score", job.score()));
        }
        put(context, "pending_proposals", pendingProposals);
        return context;
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        boolean empty = value == null || (value instanceof String s && s.isBlank())
                || (value instanceof java.util.Collection<?> c && c.isEmpty());
        if (!empty) map.put(key, value);
    }

    private static String fallbackReply(AgentState state, List<ProposedChange> proposals) {
        if (!proposals.isEmpty()) {
            return "I've prepared " + proposals.size() + " change" + (proposals.size() > 1 ? "s" : "")
                    + ". Review each one in the Changes panel before applying it.";
        }
        if (!state.getBlockedClaims().isEmpty()) {
            return "I couldn't make that change without inventing information: " + state.getBlockedClaims().getFirst()
                    + ". Can you confirm or give me the details?";
        }
        return "Tell me what you'd like to change in your CV.";
    }

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
