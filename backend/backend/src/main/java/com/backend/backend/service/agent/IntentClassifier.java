package com.backend.backend.service.agent;

import com.backend.backend.model.agent.AgentIntent;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Fast, deterministic intent detection (English and French). The model still decides which tools to
 * call; the intent is shown to the user and used to plan the turn (e.g. analyze a pasted job first).
 */
@Component
public class IntentClassifier {

    private static final Map<AgentIntent, Pattern> RULES = new java.util.LinkedHashMap<>();

    static {
        RULES.put(AgentIntent.OPTIMIZE_ATS, p("\\bats\\b|applicant tracking|keywords?|mots[- ]cl[ée]s"));
        RULES.put(AgentIntent.TAILOR_TO_JOB, p("tailor|adapt|job description|posting|position|apply(ing)? for|offre|poste|candidat|suitable for|fit (for|to)"));
        RULES.put(AgentIntent.REMOVE_CONTENT, p("\\b(remove|delete|drop|get rid of|supprim|enl[eè]v|retir)"));
        RULES.put(AgentIntent.ADD_CONTENT, p("\\b(add|include|insert|ajout|new (experience|project|job|role))"
                + "|\\b(role|company|dates?|technologies)\\s*:|\\bi (work|worked|have|had|built|developed|used|made|created)\\b"
                + "|\\bj'ai (travaill|d[ée]velopp|utilis|cr[ée])"));
        RULES.put(AgentIntent.REORDER, p("reorder|re-order|move .* (before|after|above|below|top)|order of|r[ée]organis"));
        RULES.put(AgentIntent.REWRITE, p("rewrite|rephrase|improve|emphasi[sz]e|highlight|shorten|professional|tone|action verbs"
                + "|replace|change|modify|update|switch|r[ée][ée]cri|am[ée]lior|reformul|remplac|modifi|chang"));
        RULES.put(AgentIntent.QUESTION, p("^(what|why|how|which|can you|do i|should i|est-ce|pourquoi|comment|quel)|\\?$"));
    }

    private static final List<String> JOB_MARKERS = List.of(
            "requirements", "qualifications", "we are looking", "we're looking", "what you'll do", "what we're looking",
            "you will", "you'll", "must have", "nice to have", "years of", "hiring", "we offer", "apply now", "bachelor",
            "job description", "profil recherché", "missions", "compétences requises", "vous serez", "nous recherchons");

    /**
     * Whether a user message is something the user says about themselves or an explicit instruction to
     * change their CV. Such statements count as confirmed by the user. A pasted job description, a request
     * to tailor to a job, or a question does not: "tailor my CV for a .NET job" does not mean the user knows .NET.
     */
    public boolean isSelfStatement(String message) {
        if (looksLikeJobDescription(message)) return false;
        AgentIntent intent = classify(message);
        return intent != AgentIntent.TAILOR_TO_JOB && intent != AgentIntent.OPTIMIZE_ATS && intent != AgentIntent.QUESTION;
    }

    public AgentIntent classify(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT).strip();
        if (looksLikeJobDescription(text)) return AgentIntent.TAILOR_TO_JOB;
        for (Map.Entry<AgentIntent, Pattern> rule : RULES.entrySet()) {
            if (rule.getValue().matcher(text).find()) return rule.getKey();
        }
        return AgentIntent.OTHER;
    }

    /** A long message with typical posting vocabulary is treated as a pasted job description. */
    public boolean looksLikeJobDescription(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        long markers = JOB_MARKERS.stream().filter(text::contains).count();
        return text.length() > 200 && markers >= 2;
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
