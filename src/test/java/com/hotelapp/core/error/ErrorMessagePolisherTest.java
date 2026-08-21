package com.hotelapp.core.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ErrorMessagePolisherTest {

    @Test
    void stripsEachInternalPrefix() {
        assertEquals("Db down.", ErrorMessagePolisher.polish("Error: db down", "fallback"));
        assertEquals("Bad input.", ErrorMessagePolisher.polish("Bad request: bad input", "fallback"));
        assertEquals("Bad input.", ErrorMessagePolisher.polish("Bad Request: bad input", "fallback"));
        assertEquals("Boom.", ErrorMessagePolisher.polish("Internal error: boom", "fallback"));
        assertEquals("Db down.", ErrorMessagePolisher.polish("Database error: db down", "fallback"));
        assertEquals("Exists.", ErrorMessagePolisher.polish("Conflict: exists", "fallback"));
        assertEquals("Gone.", ErrorMessagePolisher.polish("Not found: gone", "fallback"));
        assertEquals("Sign in.", ErrorMessagePolisher.polish("Unauthorized: sign in", "fallback"));
        assertEquals("Denied.", ErrorMessagePolisher.polish("Forbidden: denied", "fallback"));
    }

    @Test
    void stripsStackedPrefixesFromTheStart() {
        assertEquals("Boom.", ErrorMessagePolisher.polish("Error: Database error: boom", "fallback"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("Hi.", ErrorMessagePolisher.polish("   hi   ", "fallback"));
    }

    @Test
    void emptyMessageReturnsFallbackVerbatim() {
        assertEquals("You need to sign in to continue.",
                ErrorMessagePolisher.polish("", "You need to sign in to continue."));
        assertEquals("You need to sign in to continue.",
                ErrorMessagePolisher.polish("   ", "You need to sign in to continue."));
    }

    @Test
    void uppercasesOnlyFirstCharacterPreservingAcronyms() {
        assertEquals("2FA code required.", ErrorMessagePolisher.polish("2FA code required.", "fallback"));
        assertEquals("ID check.", ErrorMessagePolisher.polish("ID check", "fallback"));
        assertEquals("Id check.", ErrorMessagePolisher.polish("id check", "fallback"));
    }

    @Test
    void appendsTerminalPunctuationOnlyWhenMissing() {
        assertEquals("Done.", ErrorMessagePolisher.polish("done", "fallback"));
        assertEquals("Done.", ErrorMessagePolisher.polish("done.", "f"));
        assertEquals("Wow!", ErrorMessagePolisher.polish("wow!", "fallback"));
        assertEquals("Sure?", ErrorMessagePolisher.polish("sure?", "fallback"));
    }
}
