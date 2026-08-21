package com.hotelapp.core.error;

import java.util.Locale;

/**
 * Normalizes client-facing error messages into one consistent product voice:
 * trimmed, free of leaked internal prefixes, sentence-cased, and ending with
 * terminal punctuation.
 */
public final class ErrorMessagePolisher {

    private static final String[] INTERNAL_PREFIXES = {
            "Error: ",
            "Bad request: ",
            "Bad Request: ",
            "Internal error: ",
            "Database error: ",
            "Conflict: ",
            "Not found: ",
            "Unauthorized: ",
            "Forbidden: ",
    };

    private ErrorMessagePolisher() {
    }

    public static String polish(String raw, String fallback) {
        String msg = raw == null ? "" : raw.trim();

        for (String prefix : INTERNAL_PREFIXES) {
            if (msg.startsWith(prefix)) {
                msg = msg.substring(prefix.length()).trim();
            }
        }

        if (msg.isEmpty()) {
            return fallback;
        }

        String out = msg.substring(0, 1).toUpperCase(Locale.ROOT) + msg.substring(1);

        char last = out.charAt(out.length() - 1);
        if (last != '.' && last != '!' && last != '?') {
            out = out + ".";
        }
        return out;
    }
}
