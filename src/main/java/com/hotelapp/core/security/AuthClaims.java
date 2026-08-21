package com.hotelapp.core.security;

import java.util.List;

public record AuthClaims(
        String sub,
        String username,
        String iss,
        String aud,
        Long exp,
        long iat,
        List<String> roles,
        String sid) {
}
