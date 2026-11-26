package com.backend.backend.service.export;

import com.backend.backend.model.CertificationItem;
import com.backend.backend.model.EducationItem;
import com.backend.backend.model.Experience;
import com.backend.backend.model.ExportFormat;
import com.backend.backend.model.LanguageItem;
import com.backend.backend.model.ProjectItem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTabStop;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc;
import org.springframework.stereotype.Service;

/**
 * Renders the approved CV in the classic LaTeX résumé layout:
 * <ul>
 *   <li>TEX: the LaTeX source of the template, filled with the CV.</li>
 *   <li>PDF: that same LaTeX compiled with Tectonic. If no compiler is available, a built-in PDF
 *       layout is used so the download never fails.</li>
 *   <li>DOCX: an editable Word document with the same structure (serif font, ruled section titles,
 *       right-aligned dates, bullets, Technologies lines).</li>
 * </ul>
 */
@Slf4j
@Service
public class CvExportService {

    public record ExportedFile(byte[] content, String mimeType, String extension) {
    }

    private final LatexCvRenderer latex;
    private final LatexCompiler compiler;

    public CvExportService(LatexCvRenderer latex, LatexCompiler compiler) {
        this.latex = latex;
        this.compiler = compiler;
    }

    public ExportedFile render(CvDocument doc, ExportFormat format) {
        return switch (format) {
            case TEX -> new ExportedFile(latex.render(doc).getBytes(StandardCharsets.UTF_8), "application/x-tex", "tex");
            case PDF -> new ExportedFile(pdf(doc), "application/pdf", "pdf");
            case DOCX -> new ExportedFile(DocxWriter.write(doc),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        };
    }

    private byte[] pdf(CvDocument doc) {
        if (compiler.isEnabled()) {
            try {
                return compiler.compile(latex.render(doc));
            } catch (LatexCompiler.CompileException e) {
                log.warn("Falling back to the built-in PDF layout: {}", e.getMessage());
            }
        }
        return FallbackPdf.write(doc);
    }

    // ------------------------------------------------------------------ shared text helpers

    static String join(String separator, String... parts) {
        return Stream.of(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(separator));
    }

    static String dates(String start, String end) {
        return join(" – ", start, end);
    }

    static String certification(CertificationItem c) {
        return join(" — ", c.name(), c.issuer()) + (c.year().isBlank() ? "" : " (" + c.year() + ")");
    }

    static String languages(List<LanguageItem> languages) {
        return languages.stream().map(l -> l.level().isBlank() ? l.name() : l.name() + " (" + l.level() + ")")
                .collect(Collectors.joining(" — "));
    }

    static String contactLine(CvDocument.Header h) {
        List<String> parts = new ArrayList<>();
        if (!h.email().isBlank()) parts.add("Email: " + h.email());
        if (!h.phone().isBlank()) parts.add("Mobile: " + h.phone());
        if (!h.location().isBlank()) parts.add(h.location());
        return String.join(" — ", parts);
    }

    // ------------------------------------------------------------------ DOCX

    /** Word version of the template. Page: US Letter, 0.65" side margins, Cambria (serif). */
    static final class DocxWriter {
        private static final String FONT = "Cambria";
        private static final String GREY = "606060";
        private static final int TEXT_WIDTH_TWIPS = 12240 - 2 * 936;

        static byte[] write(CvDocument doc) {
            try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                margins(d);
                CvDocument.Header h = doc.header();
                if (!h.name().isBlank()) run(centered(d, 0), h.name(), 22, true, false, null);
                if (!h.title().isBlank()) run(centered(d, 2), h.title(), 13, false, false, null);
                if (!h.links().isEmpty()) {
                    XWPFParagraph p = centered(d, 0);
                    for (int i = 0; i < h.links().size(); i++) {
                        if (i > 0) run(p, "  —  ", 10, false, false, null);
                        CvDocument.Link link = h.links().get(i);
                        if (link.url() == null) {
                            run(p, link.label(), 10, false, false, null);
                        } else {
                            XWPFHyperlinkRun r = p.createHyperlinkRun(link.url());
                            style(r, link.label(), 10, false, false, null);
                        }
                    }
                }
                String contact = contactLine(h);
                if (!contact.isBlank()) run(centered(d, 4), contact.replace(" — ", "  —  "), 10, false, false, null);

                for (String key : doc.sectionOrder()) {
                if (key.equals("summary") && !doc.summary().isBlank()) {
                    heading(d, "Summary");
                    run(para(d, 0, 2), doc.summary(), 10, false, false, null);
                }
                if (key.equals("skills") && !doc.skills().isEmpty()) {
                    heading(d, "Technical Skills");
                    run(para(d, 0, 2), String.join(", ", doc.skills()), 10, false, false, null);
                }
                if (key.equals("experience") && (doc.experienceText() != null || !doc.experiences().isEmpty())) {
                    heading(d, "Professional Experience");
                    if (doc.experienceText() != null) freeText(d, doc.experienceText());
                    for (Experience e : doc.experiences()) {
                        entry(d, join(" — ", e.role(), e.company()), join("     ", e.location(), dates(e.start(), e.end())));
                        e.bullets().forEach(b -> bullet(d, b));
                        tech(d, e.technologies());
                    }
                }
                if (key.equals("projects") && (doc.projectsText() != null || !doc.projects().isEmpty())) {
                    heading(d, "Technical Projects");
                    if (doc.projectsText() != null) freeText(d, doc.projectsText());
                    for (ProjectItem p : doc.projects()) {
                        entry(d, p.name(), "");
                        if (!p.description().isBlank()) run(para(d, 0, 0), p.description(), 10, false, false, null);
                        tech(d, p.technologies());
                    }
                }
                if (key.equals("education") && !doc.education().isEmpty()) {
                    heading(d, "Education");
                    for (EducationItem e : doc.education()) {
                        entry(d, join(" — ", e.school(), e.location()), dates(e.start(), e.end()));
                        String line = join(" — ", e.degree(), e.details());
                        if (!line.isBlank()) run(para(d, 0, 2), line, 10, false, true, null);
                    }
                }
                if (key.equals("certifications") && !doc.certifications().isEmpty()) {
                    heading(d, "Certifications");
                    doc.certifications().forEach(c -> bullet(d, certification(c)));
                }
                if (key.equals("languages") && !doc.languages().isEmpty()) {
                    heading(d, "Languages");
                    run(para(d, 0, 0), languages(doc.languages()), 10, false, false, null);
                }
                if (key.equals("achievements") && !doc.achievements().isEmpty()) {
                    heading(d, "Achievements");
                    doc.achievements().forEach(a -> bullet(d, a));
                }
                }
                d.write(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void margins(XWPFDocument d) {
            CTSectPr sect = d.getDocument().getBody().isSetSectPr() ? d.getDocument().getBody().getSectPr()
                    : d.getDocument().getBody().addNewSectPr();
            var size = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
            size.setW(BigInteger.valueOf(12240));
            size.setH(BigInteger.valueOf(15840));
            CTPageMar mar = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
            mar.setLeft(BigInteger.valueOf(936));
            mar.setRight(BigInteger.valueOf(936));
            mar.setTop(BigInteger.valueOf(792));
            mar.setBottom(BigInteger.valueOf(792));
        }

        private static XWPFParagraph centered(XWPFDocument d, int afterPt) {
            XWPFParagraph p = para(d, 0, afterPt);
            p.setAlignment(ParagraphAlignment.CENTER);
            return p;
        }

        private static XWPFParagraph para(XWPFDocument d, int beforePt, int afterPt) {
            XWPFParagraph p = d.createParagraph();
            p.setSpacingBefore(beforePt * 20);
            p.setSpacingAfter(afterPt * 20);
            p.setSpacingBetween(1.0);
            return p;
        }

        private static void heading(XWPFDocument d, String title) {
            XWPFParagraph p = para(d, 10, 4);
            p.setBorderBottom(Borders.SINGLE);
            run(p, title, 14, true, false, null);
        }

        /** Bold title on the left, grey text right-aligned via a right tab stop at the text edge. */
        private static void entry(XWPFDocument d, String left, String right) {
            XWPFParagraph p = para(d, 3, 1);
            CTTabStop tab = p.getCTP().addNewPPr().addNewTabs().addNewTab();
            tab.setVal(STTabJc.RIGHT);
            tab.setPos(BigInteger.valueOf(TEXT_WIDTH_TWIPS));
            run(p, left, 10, true, false, null);
            if (!right.isBlank()) {
                p.createRun().addTab();
                run(p, right, 10, false, false, GREY);
            }
        }

        private static void bullet(XWPFDocument d, String text) {
            XWPFParagraph p = para(d, 0, 0);
            p.setIndentationLeft(360);
            p.setIndentationHanging(220);
            run(p, "•\t" + text, 10, false, false, null);
        }

        private static void tech(XWPFDocument d, List<String> technologies) {
            if (technologies.isEmpty()) return;
            run(para(d, 1, 2), "Technologies: " + String.join(", ", technologies), 9, true, false, null);
        }

        private static void freeText(XWPFDocument d, String text) {
            CvDocument.paragraphs(text).forEach(t -> run(para(d, 0, 6), t, 10, false, false, null));
        }

        private static void run(XWPFParagraph p, String text, int size, boolean bold, boolean italic, String color) {
            style(p.createRun(), text, size, bold, italic, color);
        }

        private static void style(XWPFRun r, String text, int size, boolean bold, boolean italic, String color) {
            r.setFontFamily(FONT);
            r.setFontSize(size);
            r.setBold(bold);
            r.setItalic(italic);
            if (color != null) r.setColor(color);
            String[] parts = text.split("\t", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) r.addTab();
                r.setText(parts[i], i);
            }
        }
    }

    // ------------------------------------------------------------------ fallback PDF

    /** Used only when no LaTeX compiler is available: same structure, Times font, no external tools. */
    static final class FallbackPdf {
        private static final float MARGIN = 47;
        private static final float WIDTH = PDRectangle.LETTER.getWidth() - 2 * MARGIN;

        static byte[] write(CvDocument doc) {
            try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Writer w = new Writer(document);
                PDFont bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
                PDFont regular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
                PDFont italic = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
                CvDocument.Header h = doc.header();
                if (!h.name().isBlank()) w.centered(h.name(), bold, 18);
                if (!h.title().isBlank()) w.centered(h.title(), regular, 12);
                if (!h.links().isEmpty()) {
                    w.centered(h.links().stream().map(CvDocument.Link::label).collect(Collectors.joining("  —  ")), regular, 10);
                }
                String contact = contactLine(h);
                if (!contact.isBlank()) w.centered(contact.replace(" — ", "  —  "), regular, 10);

                for (String key : doc.sectionOrder()) {
                if (key.equals("summary") && !doc.summary().isBlank()) {
                    w.heading("Summary", bold);
                    w.text(doc.summary(), regular, 10, 2);
                }
                if (key.equals("skills") && !doc.skills().isEmpty()) {
                    w.heading("Technical Skills", bold);
                    w.text(String.join(", ", doc.skills()), regular, 10, 2);
                }
                if (key.equals("experience") && (doc.experienceText() != null || !doc.experiences().isEmpty())) {
                    w.heading("Professional Experience", bold);
                    if (doc.experienceText() != null) CvDocument.paragraphs(doc.experienceText()).forEach(t -> w.text(t, regular, 10, 5));
                    for (Experience e : doc.experiences()) {
                        w.entry(join(" — ", e.role(), e.company()), join("     ", e.location(), dates(e.start(), e.end())), bold, regular);
                        e.bullets().forEach(b -> w.bullet(b, regular));
                        if (!e.technologies().isEmpty()) w.text("Technologies: " + String.join(", ", e.technologies()), bold, 9, 3);
                    }
                }
                if (key.equals("projects") && (doc.projectsText() != null || !doc.projects().isEmpty())) {
                    w.heading("Technical Projects", bold);
                    if (doc.projectsText() != null) CvDocument.paragraphs(doc.projectsText()).forEach(t -> w.text(t, regular, 10, 5));
                    for (ProjectItem p : doc.projects()) {
                        w.entry(p.name(), "", bold, regular);
                        if (!p.description().isBlank()) w.text(p.description(), regular, 10, 0);
                        if (!p.technologies().isEmpty()) w.text("Technologies: " + String.join(", ", p.technologies()), bold, 9, 3);
                    }
                }
                if (key.equals("education") && !doc.education().isEmpty()) {
                    w.heading("Education", bold);
                    for (EducationItem e : doc.education()) {
                        w.entry(join(" — ", e.school(), e.location()), dates(e.start(), e.end()), bold, regular);
                        String line = join(" — ", e.degree(), e.details());
                        if (!line.isBlank()) w.text(line, italic, 10, 3);
                    }
                }
                if (key.equals("certifications") && !doc.certifications().isEmpty()) {
                    w.heading("Certifications", bold);
                    doc.certifications().forEach(c -> w.bullet(certification(c), regular));
                }
                if (key.equals("languages") && !doc.languages().isEmpty()) {
                    w.heading("Languages", bold);
                    w.text(languages(doc.languages()), regular, 10, 2);
                }
                if (key.equals("achievements") && !doc.achievements().isEmpty()) {
                    w.heading("Achievements", bold);
                    doc.achievements().forEach(a -> w.bullet(a, regular));
                }
                }
                w.close();
                document.save(out);
                return out.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static final class Writer {
            private final PDDocument document;
            private PDPageContentStream stream;
            private float y;

            Writer(PDDocument document) throws IOException {
                this.document = document;
                newPage();
            }

            void centered(String text, PDFont font, float size) {
                String safe = safe(text, font);
                float width = width(safe, font, size);
                line(safe, font, size, MARGIN + Math.max(0, (WIDTH - width) / 2), 0, 0, 0);
            }

            void heading(String title, PDFont font) {
                gap(9);
                line(title, font, 13, MARGIN, 0, 0, 0);
                ensure(4);
                draw(() -> {
                    stream.setLineWidth(0.6f);
                    stream.moveTo(MARGIN, y - 2);
                    stream.lineTo(MARGIN + WIDTH, y - 2);
                    stream.stroke();
                });
                y -= 7;
            }

            void entry(String left, String right, PDFont bold, PDFont regular) {
                String r = safe(right, regular);
                float rightWidth = r.isBlank() ? 0 : width(r, regular, 10);
                List<String> lines = wrap(safe(left, bold), bold, 10, WIDTH - rightWidth - 12);
                for (int i = 0; i < lines.size(); i++) {
                    line(lines.get(i), bold, 10, MARGIN, 0, 0, 0);
                    if (i == 0 && !r.isBlank()) {
                        float baseline = y;
                        draw(() -> write(r, regular, 10, MARGIN + WIDTH - rightWidth, baseline, 0.38f));
                    }
                }
            }

            void bullet(String text, PDFont font) {
                List<String> lines = wrap(safe(text, font), font, 10, WIDTH - 14);
                for (int i = 0; i < lines.size(); i++) {
                    if (i == 0) {
                        String first = lines.getFirst();
                        line("•", font, 10, MARGIN + 3, 0, 0, 0);
                        float baseline = y;
                        draw(() -> write(first, font, 10, MARGIN + 14, baseline, 0));
                    } else {
                        line(lines.get(i), font, 10, MARGIN + 14, 0, 0, 0);
                    }
                }
            }

            void text(String text, PDFont font, float size, float after) {
                for (String l : wrap(safe(text, font), font, size, WIDTH)) line(l, font, size, MARGIN, 0, 0, 0);
                y -= after;
            }

            void gap(float points) {
                y -= points;
            }

            void close() throws IOException {
                stream.close();
            }

            private void line(String text, PDFont font, float size, float x, float r, float g, float b) {
                float leading = size * 1.25f;
                ensure(leading);
                y -= leading;
                float baseline = y;
                draw(() -> write(text, font, size, x, baseline, 0));
            }

            private void write(String text, PDFont font, float size, float x, float baseline, float grey) throws IOException {
                stream.beginText();
                stream.setFont(font, size);
                stream.setNonStrokingColor(grey, grey, grey);
                stream.newLineAtOffset(x, baseline);
                stream.showText(text);
                stream.endText();
            }

            private void ensure(float needed) {
                if (y - needed < MARGIN) draw(this::newPage);
            }

            private void newPage() throws IOException {
                if (stream != null) stream.close();
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                stream = new PDPageContentStream(document, page);
                y = PDRectangle.LETTER.getHeight() - MARGIN;
            }

            private interface IoAction {
                void run() throws IOException;
            }

            private static void draw(IoAction action) {
                try {
                    action.run();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            private static float width(String text, PDFont font, float size) {
                try {
                    return font.getStringWidth(text) / 1000 * size;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            private static List<String> wrap(String text, PDFont font, float size, float maxWidth) {
                List<String> lines = new ArrayList<>();
                StringBuilder current = new StringBuilder();
                for (String word : text.split("\\s+")) {
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (width(candidate, font, size) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                if (!current.isEmpty()) lines.add(current.toString());
                return lines;
            }

            /** Standard PDF fonts only cover WinAnsi; anything else becomes "?" instead of failing. */
            private static String safe(String text, PDFont font) {
                StringBuilder out = new StringBuilder();
                text.replace('\t', ' ').replace('\n', ' ').codePoints().forEach(cp -> {
                    String ch = new String(Character.toChars(cp));
                    try {
                        font.encode(ch);
                        out.append(ch);
                    } catch (IOException | IllegalArgumentException e) {
                        out.append('?');
                    }
                });
                return out.toString();
            }
        }
    }
}
