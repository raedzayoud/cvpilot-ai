package com.backend.backend.service.export;

import com.backend.backend.model.CertificationItem;
import com.backend.backend.model.EducationItem;
import com.backend.backend.model.Experience;
import com.backend.backend.model.LanguageItem;
import com.backend.backend.model.ProjectItem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Writes the CV as LaTeX in the classic one-column résumé layout: centered header, ruled section
 * titles, "Role — Company" with grey location/dates on the right, bullet points and a bold
 * Technologies line. All user text is escaped, so CV content can never inject LaTeX commands.
 */
@Component
public class LatexCvRenderer {

    private final String preamble;

    public LatexCvRenderer() {
        try (InputStream in = getClass().getResourceAsStream("/latex/cv-preamble.tex")) {
            if (in == null) throw new IllegalStateException("latex/cv-preamble.tex is missing");
            this.preamble = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String render(CvDocument doc) {
        StringBuilder out = new StringBuilder(preamble.stripTrailing()).append("\n\n\\begin{document}\n\n");
        header(out, doc.header());
        for (String key : doc.sectionOrder()) section(out, key, doc);
        return out.append("\n\\end{document}\n").toString();
    }

    private static void section(StringBuilder out, String key, CvDocument doc) {
        String title = com.backend.backend.model.CvSections.LABELS.get(key);
        switch (key) {
            case "summary" -> {
                if (doc.summary().isBlank()) return;
                section(out, title);
                out.append(escape(doc.summary())).append("\n");
            }
            case "skills" -> {
                if (doc.skills().isEmpty()) return;
                section(out, title);
                out.append(escape(String.join(", ", doc.skills()))).append("\n");
            }
            case "experience" -> {
                if (doc.experienceText() != null) {
                    section(out, title);
                    freeText(out, doc.experienceText());
                } else if (!doc.experiences().isEmpty()) {
                    section(out, title);
                    for (Experience e : doc.experiences()) experience(out, e);
                }
            }
            case "projects" -> {
                if (doc.projectsText() != null) {
                    section(out, title);
                    freeText(out, doc.projectsText());
                } else if (!doc.projects().isEmpty()) {
                    section(out, title);
                    for (ProjectItem p : doc.projects()) project(out, p);
                }
            }
            case "education" -> {
                if (doc.education().isEmpty()) return;
                section(out, title);
                for (EducationItem e : doc.education()) education(out, e);
            }
            case "certifications" -> {
                if (doc.certifications().isEmpty()) return;
                section(out, title);
                itemize(out, doc.certifications().stream().map(LatexCvRenderer::certification).toList());
            }
            case "languages" -> {
                if (doc.languages().isEmpty()) return;
                section(out, title);
                out.append(doc.languages().stream().map(LatexCvRenderer::language).collect(Collectors.joining("\\cvsep "))).append("\n");
            }
            case "achievements" -> {
                if (doc.achievements().isEmpty()) return;
                section(out, title);
                itemize(out, doc.achievements().stream().map(LatexCvRenderer::escape).toList());
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ blocks

    private static void header(StringBuilder out, CvDocument.Header h) {
        out.append("\\begin{center}\n");
        if (!h.name().isBlank()) out.append("  {\\LARGE\\bfseries ").append(escape(h.name())).append("}\\\\[2pt]\n");
        if (!h.title().isBlank()) out.append("  {\\large ").append(escape(h.title())).append("}\\\\[3pt]\n");
        List<String> links = h.links().stream()
                .map(l -> l.url() == null ? escape(l.label()) : "\\href{" + url(l.url()) + "}{" + escape(l.label()) + "}")
                .toList();
        if (!links.isEmpty()) out.append("  ").append(String.join("\\cvsep ", links)).append("\\\\\n");
        List<String> contact = new ArrayList<>();
        if (!h.email().isBlank()) contact.add("Email: \\href{mailto:" + url(h.email()) + "}{" + escape(h.email()) + "}");
        if (!h.phone().isBlank()) contact.add("Mobile: " + escape(h.phone()));
        if (!h.location().isBlank()) contact.add(escape(h.location()));
        if (!contact.isEmpty()) out.append("  ").append(String.join("\\cvsep ", contact)).append("\n");
        out.append("\\end{center}\n\\vspace{-8pt}\n");
    }

    private static void section(StringBuilder out, String title) {
        out.append("\n\\section*{").append(escape(title)).append("}\n");
    }

    private static void experience(StringBuilder out, Experience e) {
        String title = join("\\cvsep ", escape(e.role()), escape(e.company()));
        out.append("\\cvheading{").append(title).append("}{").append(meta(e.location(), dates(e.start(), e.end()))).append("}\n");
        itemize(out, e.bullets().stream().map(LatexCvRenderer::escape).toList(), !e.technologies().isEmpty());
        if (!e.technologies().isEmpty()) out.append("\\cvtech{").append(escape(String.join(", ", e.technologies()))).append("}\n");
        out.append("\\vspace{3pt}\n");
    }

    private static void project(StringBuilder out, ProjectItem p) {
        String name = escape(p.name());
        if (!p.link().isBlank()) {
            CvDocument.Link link = CvDocumentFactory.link(p.link());
            if (link.url() != null) name = "\\href{" + url(link.url()) + "}{" + name + "}";
        }
        out.append("\\cvheading{").append(name).append("}{}\n");
        if (!p.description().isBlank()) out.append(escape(p.description())).append("\\par\n");
        if (!p.technologies().isEmpty()) out.append("\\cvtech{").append(escape(String.join(", ", p.technologies()))).append("}\n");
        out.append("\\vspace{3pt}\n");
    }

    private static void education(StringBuilder out, EducationItem e) {
        String title = join("\\cvsep ", escape(e.school()), escape(e.location()));
        out.append("\\cvheading{").append(title).append("}{").append(escape(dates(e.start(), e.end()))).append("}\n");
        String line = join("\\cvsep ", escape(e.degree()), escape(e.details()));
        if (!line.isEmpty()) out.append("\\textit{").append(line).append("}\\par\n");
        out.append("\\vspace{2pt}\n");
    }

    private static String certification(CertificationItem c) {
        String text = join("\\cvsep ", escape(c.name()), escape(c.issuer()));
        return c.year().isBlank() ? text : text + " (" + escape(c.year()) + ")";
    }

    private static String language(LanguageItem l) {
        return escape(l.name()) + (l.level().isBlank() ? "" : " (" + escape(l.level()) + ")");
    }

    private static void freeText(StringBuilder out, String text) {
        for (String paragraph : CvDocument.paragraphs(text)) out.append(escape(paragraph)).append("\\par\\medskip\n");
    }

    private static void itemize(StringBuilder out, List<String> items) {
        itemize(out, items, false);
    }

    /**
     * @param keepWithNext forbid a page break between the list and what follows. The penalty must come
     *                     before the list's closing space: a break at glue is only legal right after a
     *                     line, never after a penalty.
     */
    private static void itemize(StringBuilder out, List<String> items, boolean keepWithNext) {
        if (items.isEmpty()) return;
        out.append("\\begin{itemize}\n");
        for (String item : items) out.append("  \\item ").append(item).append("\n");
        if (keepWithNext) out.append("  \\cvkeep\n");
        out.append("\\end{itemize}\n");
    }

    private static String meta(String location, String dates) {
        String loc = escape(location);
        String when = escape(dates);
        if (loc.isEmpty()) return when;
        if (when.isEmpty()) return loc;
        return loc + "\\hspace{1.2em}" + when;
    }

    private static String dates(String start, String end) {
        return join(" – ", start, end);
    }

    private static String join(String separator, String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(separator));
    }

    // ------------------------------------------------------------------ escaping

    /** Escapes LaTeX special characters; line breaks become spaces. */
    static String escape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (char c : text.replace("\r", "").replace('\n', ' ').toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\textbackslash{}");
                case '&', '%', '$', '#', '_', '{', '}' -> out.append('\\').append(c);
                case '~' -> out.append("\\textasciitilde{}");
                case '^' -> out.append("\\textasciicircum{}");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Makes a URL safe inside \href: keeps common URL characters, percent-encodes the rest, and escapes
     * % and # as hyperref requires. Prevents breaking out of the argument with braces or backslashes.
     */
    static String url(String raw) {
        StringBuilder out = new StringBuilder();
        String value = raw.strip();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) && c < 128 || ":/?=._-@+,;!*'()".indexOf(c) >= 0) {
                out.append(c);
            } else if (c == '%' && i + 2 < value.length() && isHex(value.charAt(i + 1)) && isHex(value.charAt(i + 2))) {
                out.append("\\%");
            } else if (c == '#') {
                out.append("\\#");
            } else {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    out.append("\\%").append(String.format("%02X", b & 0xFF));
                }
            }
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return Character.digit(c, 16) >= 0;
    }
}
