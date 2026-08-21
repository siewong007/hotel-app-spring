package com.hotelapp.core.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps {@link ApiError} to HTTP responses exactly as the Rust
 * {@code IntoResponse for ApiError} implementation does.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String GENERIC_500_MESSAGE =
            "Something went wrong on our end. Please try again.";

    @ExceptionHandler(ApiError.class)
    public ResponseEntity<Map<String, Object>> handleApiError(ApiError error) {
        HttpStatus status = switch (error.kind()) {
            case DATABASE -> {
                log.error("Database error: {}", error.message());
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
            case INTERNAL -> {
                log.error("Internal error: {}", error.message());
                yield HttpStatus.INTERNAL_SERVER_ERROR;
            }
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TOO_MANY_REQUESTS, TOO_MANY_REQUESTS_RETRY_AFTER -> HttpStatus.TOO_MANY_REQUESTS;
            case PROFILE_INCOMPLETE -> HttpStatus.UNPROCESSABLE_ENTITY;
        };

        if (error.kind() == ApiError.Kind.PROFILE_INCOMPLETE) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Complete your profile before making a booking.");
            body.put("code", "profile_incomplete");
            body.put("missing_profile_fields", error.missingFields());
            return ResponseEntity.status(status).body(body);
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

        if (error.kind() == ApiError.Kind.TOO_MANY_REQUESTS_RETRY_AFTER) {
            return ResponseEntity.status(status)
                    .header("Retry-After", String.valueOf(error.retryAfterSecs()))
                    .body(body);
        }

        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException exception) {
        String firstMessage = exception.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("");
        return badRequest(firstMessage);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        String firstMessage = exception.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("");
        return badRequest(firstMessage);
    }

    private ResponseEntity<Map<String, Object>> badRequest(String firstMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ErrorMessagePolisher.polish(
                firstMessage, "That request couldn't be processed."));
        return ResponseEntity.badRequest().body(body);
    }
}
