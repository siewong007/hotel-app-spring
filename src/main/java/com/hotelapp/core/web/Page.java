package com.hotelapp.core.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of models::PaginatedResponse: {data, total, page, page_size}.
 */
public final class Page {

    private Page() {
    }

    public static <T> Map<String, Object> of(List<T> data, long total, long page, long pageSize) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("total", total);
        body.put("page", page);
        body.put("page_size", pageSize);
        return body;
    }

    public static long page(Map<String, String> query) {
        return parse(query.get("page"), 1, 1);
    }

    public static long pageSize(Map<String, String> query) {
        return parse(query.get("page_size"), 20, 1);
    }

    public static long parse(String raw, long fallback, long min) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return Math.max(min, value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static long offset(long page, long pageSize) {
        return (page - 1) * pageSize;
    }
}
