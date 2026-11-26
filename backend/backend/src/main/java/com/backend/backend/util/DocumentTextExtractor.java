package com.backend.backend.util;

import com.backend.backend.exception.ApiException;
import com.backend.backend.validation.UploadValidator.Kind;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** Plain-text extraction from PDF, DOCX and LaTeX uploads. */
@Component
public class DocumentTextExtractor {

    private static final int MAX_PDF_PAGES = 30;

    public String extract(Kind kind, byte[] bytes) {
        try {
            String text = switch (kind) {
                case PDF -> pdf(bytes);
                case DOCX -> docx(bytes);
                case TEX -> new String(bytes, StandardCharsets.UTF_8);
            };
            return normalize(text);
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("The " + kind.name() + " file could not be read. It may be corrupted or password-protected.");
        }
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) throw new IOException("encrypted");
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), MAX_PDF_PAGES));
            return stripper.getText(document) + hyperlinks(document);
        }
    }

    /**
     * Link targets are not part of a PDF's text ("LinkedIn" is shown, the URL is an annotation), so
     * list them after the text; the extraction prompt uses them to fill the profile links.
     */
    private static String hyperlinks(PDDocument document) throws IOException {
        java.util.Set<String> urls = new java.util.LinkedHashSet<>();
        int pages = Math.min(document.getNumberOfPages(), MAX_PDF_PAGES);
        for (int i = 0; i < pages && urls.size() < 20; i++) {
            for (var annotation : document.getPage(i).getAnnotations()) {
                if (annotation instanceof org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink link
                        && link.getAction() instanceof org.apache.pdfbox.pdmodel.interactive.action.PDActionURI uri
                        && uri.getURI() != null && uri.getURI().matches("(?i)https?://.+")) {
                    urls.add(uri.getURI().trim());
                }
            }
        }
        return urls.isEmpty() ? "" : "\n\nHyperlinks in the document:\n- " + String.join("\n- ", urls);
    }

    private static String docx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace(' ', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
