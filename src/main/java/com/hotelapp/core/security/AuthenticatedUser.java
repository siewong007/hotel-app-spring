package com.hotelapp.core.security;

public record AuthenticatedUser(long userId, AuthClaims claims) {
}
