package com.hotelapp.core.error;

import java.util.List;

/**
 * Unified API error type used across all handlers, ported from the Rust
 * {@code core/error.rs} ApiError enum.
 */
public final class ApiError extends RuntimeException {

    public enum Kind {
        DATABASE,
        UNAUTHORIZED,
        FORBIDDEN,
        BAD_REQUEST,
        UNPROCESSABLE_ENTITY,
        NOT_FOUND,
        CONFLICT,
        INTERNAL,
        SERVICE_UNAVAILABLE,
        TOO_MANY_REQUESTS,
        TOO_MANY_REQUESTS_RETRY_AFTER,
        PROFILE_INCOMPLETE,
        TWO_FACTOR_ENROLLMENT_REQUIRED,
        GUEST_NAME_TAKEN
    }

    private final Kind kind;
    private final Long retryAfterSecs;
    private final List<String> missingFields;

    private ApiError(Kind kind, String message, Long retryAfterSecs, List<String> missingFields) {
        super(message);
        this.kind = kind;
        this.retryAfterSecs = retryAfterSecs;
        this.missingFields = missingFields;
    }

    public String message() {
        return getMessage();
    }

    public Kind kind() {
        return kind;
    }

    public Long retryAfterSecs() {
        return retryAfterSecs;
    }

    public List<String> missingFields() {
        return missingFields;
    }

    public static ApiError database(String message) {
        return new ApiError(Kind.DATABASE, message, null, null);
    }

    public static ApiError unauthorized(String message) {
        return new ApiError(Kind.UNAUTHORIZED, message, null, null);
    }

    public static ApiError forbidden(String message) {
        return new ApiError(Kind.FORBIDDEN, message, null, null);
    }

    public static ApiError badRequest(String message) {
        return new ApiError(Kind.BAD_REQUEST, message, null, null);
    }

    public static ApiError unprocessableEntity(String message) {
        return new ApiError(Kind.UNPROCESSABLE_ENTITY, message, null, null);
    }

    public static ApiError notFound(String message) {
        return new ApiError(Kind.NOT_FOUND, message, null, null);
    }

    public static ApiError conflict(String message) {
        return new ApiError(Kind.CONFLICT, message, null, null);
    }

    public static ApiError internal(String message) {
        return new ApiError(Kind.INTERNAL, message, null, null);
    }

    public static ApiError serviceUnavailable(String message) {
        return new ApiError(Kind.SERVICE_UNAVAILABLE, message, null, null);
    }

    public static ApiError tooManyRequests(String message) {
        return new ApiError(Kind.TOO_MANY_REQUESTS, message, null, null);
    }

    public static ApiError tooManyRequestsRetryAfter(String message, long retryAfterSecs) {
        return new ApiError(Kind.TOO_MANY_REQUESTS_RETRY_AFTER, message, retryAfterSecs, null);
    }

    public static ApiError profileIncomplete(List<String> missingFields) {
        return new ApiError(Kind.PROFILE_INCOMPLETE, null, null, List.copyOf(missingFields));
    }

    public static ApiError twoFactorEnrollmentRequired() {
        return new ApiError(Kind.TWO_FACTOR_ENROLLMENT_REQUIRED,
                "Your role requires two-factor authentication. Ask an administrator to "
                        + "help you finish setting it up.", null, null);
    }

    public static ApiError guestNameTaken() {
        return new ApiError(Kind.GUEST_NAME_TAKEN,
                "This nickname is already used. Please choose another.", null, null);
    }
}
