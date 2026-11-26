package com.backend.backend.service.ai;

import com.backend.backend.config.AppProperties;
import com.backend.backend.integration.ai.AiChatClient;
import com.backend.backend.integration.ai.AiException;
import com.backend.backend.integration.ai.AiRequest;
import com.backend.backend.integration.ai.AiRequest.Message;
import com.backend.backend.model.CvProfile;
import com.backend.backend.model.PersonalInfo;
import com.backend.backend.service.matching.SkillDictionary;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/** Turns raw CV text into a structured {@link CvProfile} using the AI provider, with a keyword fallback. */
@Slf4j
@Service
public class CvExtractionService {

    public record Result(CvProfile profile, String language, String note) {
    }

    private static final String SYSTEM_PROMPT = """
            You extract structured data from a CV (résumé). Rules:
            - Copy facts exactly as written. Never invent, infer, embellish or translate anything.
            - Keep the CV's original language for every value.
            - If a field is absent, use "" or []. Dates stay in the CV's own format (e.g. "Mar 2022", "2020", "Present").
            - "skills" lists technologies/tools/methods named in the CV, one per item, as written.
            - Each experience bullet is one achievement or responsibility sentence from the CV.
            - A "Technologies:" / "Stack:" line under an experience goes in that experience's "technologies" list,
              not in its bullets. The same line under a project goes in the project's "technologies".
            - "achievements": awards, rankings, competitions and similar lines (e.g. an "Achievements" section).
            - "links": when a "Hyperlinks in the document" list is given, use those full URLs (profile/portfolio
              links only, not email); otherwise copy the link labels or URLs written in the CV.
            - "language" is the English name of the language the CV is written in (e.g. "English", "French").
            Return ONLY a JSON object with exactly this shape:
            {"language":"","profile":{"personalInfo":{"fullName":"","email":"","phone":"","location":"","links":[]},
            "professionalTitle":"","summary":"","skills":[],
            "experiences":[{"role":"","company":"","location":"","start":"","end":"","bullets":[],"technologies":[]}],
            "education":[{"degree":"","school":"","location":"","start":"","end":"","details":""}],
            "projects":[{"name":"","description":"","technologies":[],"link":""}],
            "certifications":[{"name":"","issuer":"","year":""}],
            "languages":[{"name":"","level":""}],
            "achievements":[]}}
            """;

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d ().-]{7,}\\d");
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://)?(?:www\\.)?(?:linkedin\\.com|github\\.com|gitlab\\.com)/[\\w./-]+");

    private final AiChatClient ai;
    private final AiJson aiJson;
    private final int maxInputChars;

    public CvExtractionService(AiChatClient ai, AiJson aiJson, AppProperties properties) {
        this.ai = ai;
        this.aiJson = aiJson;
        this.maxInputChars = properties.ai().maxInputChars();
    }

    public Result extract(String cvText) {
        if (cvText.isBlank()) {
            return new Result(CvProfile.empty(), "English",
                    "No text could be read from this file (it may be a scanned image). Fill in your profile below.");
        }
        if (!ai.isConfigured()) {
            return fallback(cvText, "AI extraction is not configured on the server, so only contact details and known "
                    + "skills were detected. Complete the rest of your profile below.");
        }
        try {
            String content = ai.complete(new AiRequest("cv-extraction",
                    List.of(Message.system(SYSTEM_PROMPT), Message.user("CV text:\n\"\"\"\n" + AiJson.clip(cvText, maxInputChars) + "\n\"\"\"")),
                    true, 0.0, 8000));
            JsonNode root = aiJson.parseObject(content);
            CvProfile profile = aiJson.convert(root.path("profile"), CvProfile.class).withItemIds().withSkillsFromTechnologies();
            String language = cleanLanguage(root.path("language").asString(""));
            if (profile.isBlank()) {
                return fallback(cvText, "The AI could not find CV sections in this file. Complete your profile below.");
            }
            return new Result(profile, language, null);
        } catch (AiException e) {
            log.warn("CV extraction fell back to keyword detection: {}", e.getKind());
            return fallback(cvText, "Automatic extraction failed (" + e.getUserMessage() + ") Only contact details and "
                    + "known skills were detected; complete your profile below.");
        }
    }

    private static Result fallback(String raw, String note) {
        String text = stripLatex(raw);
        PersonalInfo info = new PersonalInfo(
                firstLine(text), first(EMAIL, text), first(PHONE, text), "",
                LINK.matcher(text).results().map(java.util.regex.MatchResult::group).distinct().limit(5).toList());
        CvProfile profile = new CvProfile(info, "", "", SkillDictionary.findIn(text),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new Result(profile, guessLanguage(text), note);
    }

    /** Drops LaTeX comments and preamble-only lines, and unwraps commands, so "\section*{Name}" becomes "Name". */
    static String stripLatex(String text) {
        if (!text.contains("\\")) return text;
        return text.lines()
                .map(line -> line.replaceAll("(?<!\\\\)%.*$", ""))
                .filter(line -> !line.trim().matches("\\\\(documentclass|usepackage|begin|end|newcommand|renewcommand|def|setlength|pagestyle)\\b.*"))
                .map(line -> line.replaceAll("\\\\[a-zA-Z]+\\*?(\\[[^\\]]*\\])?", " ")
                        .replaceAll("[{}]", " ")
                        .replace("\\%", "%")
                        .replaceAll("\\s{2,}", " ")
                        .trim())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static String first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group().trim() : "";
    }

    private static String firstLine(String text) {
        return text.lines().map(String::trim).filter(l -> !l.isEmpty()).findFirst()
                .filter(l -> l.length() <= 60 && !EMAIL.matcher(l).find())
                .orElse("");
    }

    private static String cleanLanguage(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty() || v.length() > 40) return "English";
        return v.substring(0, 1).toUpperCase(Locale.ROOT) + v.substring(1);
    }

    /** Rough French/English detection for the fallback path only. */
    private static String guessLanguage(String text) {
        String lower = " " + text.toLowerCase(Locale.ROOT) + " ";
        long fr = Stream.of(" expérience", " compétences", " formation", " développeur", " ingénieur", " langues ")
                .filter(lower::contains).count();
        return fr >= 2 ? "French" : "English";
    }
}
