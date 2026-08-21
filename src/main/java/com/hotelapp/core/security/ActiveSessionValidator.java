package com.hotelapp.core.security;

public interface ActiveSessionValidator {

    boolean isActive(long userId, String sessionId);
}
