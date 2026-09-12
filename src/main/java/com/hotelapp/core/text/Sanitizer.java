package com.hotelapp.core.text;

/**
 * Port of utils/sanitization.rs — the free-text cleaning every mutating
 * handler runs before persistence.
 */
public final class Sanitizer {

    private Sanitizer() {
    }

    /** Removes control characters, keeping newline/CR/tab like upstream. */
    public static String sanitizeText(String input) {
        StringBuilder out = new StringBuilder(input.length());
        input.codePoints().forEach(c -> {
            if (!Character.isISOControl(c) || c == '\n' || c == '\r' || c == '\t' || c == ' ') {
                out.appendCodePoint(c);
            }
        });
        return out.toString();
    }

    /** Trims and lowercases for consistent storage. */
    public static String sanitizeEmail(String input) {
        return input.trim().toLowerCase();
    }

    /** Keeps digits plus an optional leading '+', drops everything else. */
    public static String sanitizePhone(String input) {
        String trimmed = input.trim();
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isDigit(c) || (i == 0 && c == '+')) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Display name cleaning: control characters out, edges trimmed. */
    public static String sanitizeGuestName(String name) {
        return sanitizeText(name).trim();
    }

    /** Notes cleaning: markup stripped first, then control characters. */
    public static String sanitizeNotes(String notes) {
        return sanitizeText(notes.replaceAll("<[^>]*>", ""));
    }
}
