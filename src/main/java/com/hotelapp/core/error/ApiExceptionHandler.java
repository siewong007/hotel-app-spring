package com.hotelapp.core.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @ExceptionHandler(ApiError.class)
    public ResponseEntity<Map<String, Object>> handleApiError(ApiError error) {
        return ResponseEntity.status(ApiErrorResponses.statusOf(error))
                .headers(headers -> {
                    if (error.kind() == ApiError.Kind.TOO_MANY_REQUESTS_RETRY_AFTER) {
                        headers.set("Retry-After", String.valueOf(error.retryAfterSecs()));
                    }
                })
                .body(ApiErrorResponses.bodyOf(error));
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
