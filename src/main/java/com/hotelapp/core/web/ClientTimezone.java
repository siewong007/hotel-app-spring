package com.hotelapp.core.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Port of upstream {@code routes/mod.rs::extract_client_timezone} +
 * {@code sanitize_client_timezone}: reads the browser's IANA timezone from
 * {@code x-client-timezone} and validates the shape — ≤64 chars, ≤3
 * `/`-separated segments of alnum/`_`/`-`/`+`. The value is rendered back to
 * the account owner, so anything unexpected is dropped rather than stored.
 */
public final class ClientTimezone {

    public static final String HEADER = "x-client-timezone";

    private ClientTimezone() {
    }

    public static String extract(HttpServletRequest request) {
        return sanitize(request.getHeader(HEADER));
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.isEmpty() || value.length() > 64) {
            return null;
        }
        // "UTC" and "Asia/Kuala_Lumpur" are both valid; "America/Argentina/Salta"
        // is the deepest real shape, so cap at three segments.
        String[] segments = value.split("/", -1);
        if (segments.length > 3) {
            return null;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || !segment.chars().allMatch(ClientTimezone::segmentChar)) {
                return null;
            }
        }
        return value;
    }

    private static boolean segmentChar(int c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '+';
    }
}
