package com.backend.backend.util;

import com.backend.backend.exception.ApiException;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds a Pageable from query params, allowing only whitelisted sort fields. */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /**
     * @param sort      "field" or "field,asc|desc"
     * @param allowed   public sort name to entity property
     * @param fallback  default sort when none is given
     */
    public static Pageable of(int page, int size, String sort, Map<String, String> allowed, Sort fallback) {
        if (page < 0) throw ApiException.badRequest("page must be 0 or greater.");
        if (size < 1 || size > MAX_SIZE) throw ApiException.badRequest("size must be between 1 and " + MAX_SIZE + ".");
        return PageRequest.of(page, size, parse(sort, allowed, fallback));
    }

    private static Sort parse(String sort, Map<String, String> allowed, Sort fallback) {
        if (sort == null || sort.isBlank()) return fallback;
        String[] parts = sort.split(",");
        String property = allowed.get(parts[0].trim());
        if (property == null) {
            throw ApiException.badRequest("Cannot sort by '" + parts[0].trim() + "'. Allowed: " + String.join(", ", allowed.keySet()) + ".");
        }
        boolean asc = parts.length > 1 && parts[1].trim().toLowerCase(Locale.ROOT).equals("asc");
        return Sort.by(asc ? Sort.Direction.ASC : Sort.Direction.DESC, property);
    }

    /** Lowercase LIKE pattern with wildcards escaped; "%" when the search is empty. */
    public static String likePattern(String search) {
        if (search == null || search.isBlank()) return "%";
        String escaped = search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
