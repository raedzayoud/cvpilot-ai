package com.backend.backend.service.matching;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whole-token matching that treats "+", "#" and "." as part of a token, so "C" does not match "C++"
 * and "Java" does not match "JavaScript".
 */
public final class TextMatcher {

    private static final int CACHE_LIMIT = 5_000;
    private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>();

    private TextMatcher() {
    }

    public static boolean mentions(String text, String token) {
        if (text == null || token == null || token.isBlank()) return false;
        return pattern(token).matcher(text).find();
    }

    /** Replaces whole-token occurrences of {@code from} with {@code to} (so "Java" never touches "JavaScript"). */
    public static String replace(String text, String from, String to) {
        if (text == null || text.isEmpty() || from == null || from.isBlank()) return text;
        Pattern compiled = Pattern.compile(
                "(?<![a-z0-9+#.])" + Pattern.quote(from.trim()) + "(?![a-z0-9+#])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return compiled.matcher(text).replaceAll(Matcher.quoteReplacement(to == null ? "" : to));
    }

    private static Pattern pattern(String token) {
        String key = token.trim().toLowerCase(Locale.ROOT);
        Pattern cached = CACHE.get(key);
        if (cached != null) return cached;
        Pattern compiled = Pattern.compile(
                // A trailing "." only counts as punctuation when the sentence ends ("...with Java.").
                "(?:^|[^a-z0-9+#.])" + Pattern.quote(key) + "(?:$|[^a-z0-9+#.]|\\.(?:$|\\s))",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        if (CACHE.size() < CACHE_LIMIT) CACHE.put(key, compiled);
        return compiled;
    }
}
