package com.hotelapp.auth;

import org.springframework.http.ResponseCookie;

public final class RefreshCookie {

    public static final String NAME = "refresh_token";
    public static final String PATH = "/api/auth";
    public static final long MAX_AGE_SECS = 30L * 24 * 60 * 60;

    private RefreshCookie() {
    }

    public static String build(String token, boolean secure) {
        return cookie(token, MAX_AGE_SECS, secure);
    }

    public static String clear(boolean secure) {
        return cookie("", 0, secure);
    }

    private static String cookie(String value, long maxAgeSecs, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(PATH)
                .maxAge(maxAgeSecs)
                .build();
        return cookie.toString();
    }
}
