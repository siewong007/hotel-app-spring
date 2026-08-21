package com.hotelapp.core.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public final class ApiErrorResponses {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorResponses.class);

    private static final String GENERIC_500_MESSAGE =
            "Something went wrong on our end. Please try again.";

    private ApiErrorResponses() {
    }

    public static HttpStatus statusOf(ApiError error) {
        return switch (error.kind()) {
            case DATABASE, INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_RETRY_AFTER -> HttpStatus.TOO_MANY_REQUESTS;
            case PROFILE_INCOMPLETE -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    public static Map<String, Object> bodyOf(ApiError error) {
        if (error.kind() == ApiError.Kind.PROFILE_INCOMPLETE) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Complete your profile before making a booking.");
            body.put("code", "profile_incomplete");
            body.put("missing_profile_fields", error.missingFields());
            return body;
        }
        String message = switch (error.kind()) {
            case DATABASE, INTERNAL -> GENERIC_500_MESSAGE;
            case UNAUTHORIZED ->
                ErrorMessagePolisher.polish(error.message(), "You need to sign in to continue.");
            case FORBIDDEN ->
                ErrorMessagePolisher.polish(error.message(), "You don't have permission to do that.");
            case BAD_REQUEST ->
                ErrorMessagePolisher.polish(error.message(), "That request couldn't be processed.");
            case NOT_FOUND ->
                ErrorMessagePolisher.polish(error.message(), "We couldn't find what you were looking for.");
            case CONFLICT ->
                ErrorMessagePolisher.polish(error.message(), "That action conflicts with the current state.");
            case SERVICE_UNAVAILABLE ->
                ErrorMessagePolisher.polish(error.message(), "This service is temporarily unavailable.");
            case TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_RETRY_AFTER ->
                ErrorMessagePolisher.polish(error.message(), "Too many requests. Please slow down and try again.");
            case PROFILE_INCOMPLETE -> "Complete your profile before making a booking.";
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return body;
    }

    public static void write(ApiError error, HttpServletResponse response) throws IOException {
        if (error.kind() == ApiError.Kind.DATABASE || error.kind() == ApiError.Kind.INTERNAL) {
            log.error("{} error: {}", error.kind(), error.message());
        }
        response.setStatus(statusOf(error).value());
        response.setContentType("application/json");
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : bodyOf(error).entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':');
            json.append(entry.getValue() instanceof Iterable<?> ? iterableJson((Iterable<?>) entry.getValue())
                    : quote(String.valueOf(entry.getValue())));
        }
        json.append('}');
        if (error.kind() == ApiError.Kind.TOO_MANY_REQUESTS_RETRY_AFTER) {
            response.setHeader("Retry-After", String.valueOf(error.retryAfterSecs()));
        }
        response.getWriter().write(json.toString());
    }

    private static String iterableJson(Iterable<?> items) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Object item : items) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(String.valueOf(item)));
        }
        return json.append(']').toString();
    }

    private static String quote(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"').toString();
    }
}
