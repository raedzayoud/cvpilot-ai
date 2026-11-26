package com.backend.backend.validation;

import com.backend.backend.exception.ApiException;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates uploads by extension AND content signature, so a renamed executable or archive is
 * rejected even if the browser-supplied content type looks right.
 */
@Component
public class UploadValidator {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    public enum Kind { PDF, DOCX, TEX }

    public record ValidatedUpload(String fileName, Kind kind, byte[] bytes) {
    }

    public ValidatedUpload validate(MultipartFile file, Set<Kind> allowed) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file was provided.");
        if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("That file is larger than 10 MB.");

        String fileName = sanitizeFileName(file.getOriginalFilename());
        Kind kind = kindOf(fileName);
        if (kind == null || !allowed.contains(kind)) {
            throw ApiException.badRequest("Choose a " + describe(allowed) + " file.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("The file could not be read.");
        }
        if (!signatureMatches(kind, bytes)) {
            throw ApiException.badRequest("The file content does not look like a valid " + kind.name() + " file.");
        }
        return new ValidatedUpload(fileName, kind, bytes);
    }

    /** Keeps only the base name and safe characters; prevents path tricks and header injection on download. */
    public static String sanitizeFileName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 20 ? name.substring(dot) : "";
            name = name.substring(0, 200 - ext.length()) + ext;
        }
        return name.isEmpty() ? "file" : name;
    }

    private static Kind kindOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return Kind.PDF;
        if (lower.endsWith(".docx")) return Kind.DOCX;
        if (lower.endsWith(".tex")) return Kind.TEX;
        return null;
    }

    private static boolean signatureMatches(Kind kind, byte[] bytes) {
        return switch (kind) {
            case PDF -> startsWith(bytes, new byte[] {'%', 'P', 'D', 'F'});
            case DOCX -> startsWith(bytes, new byte[] {'P', 'K', 3, 4});
            case TEX -> looksLikeText(bytes);
        };
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private static boolean looksLikeText(byte[] bytes) {
        int sample = Math.min(bytes.length, 8192);
        for (int i = 0; i < sample; i++) if (bytes[i] == 0) return false;
        return bytes.length > 0;
    }

    private static String describe(Set<Kind> allowed) {
        return String.join(", ", allowed.stream().map(k -> k == Kind.TEX ? "LaTeX (.tex)" : k.name()).sorted().toList())
                .replaceFirst(", ([^,]+)$", " or $1");
    }
}
