package com.hotelapp.auth;

import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of {@code services/turnstile.rs}: Cloudflare Turnstile verification for
 * the public auth surfaces. The widget token rides the
 * {@code cf-turnstile-response} header so request bodies stay untouched.
 *
 * <p>Fail closed while configured: once enabled with both distinct keys, a
 * missing/invalid token is rejected and an unreachable Cloudflare is a 503.
 * Operator faults (a duplicated or wrong secret) surface as 503, never as a
 * visitor-facing challenge failure.
 */
@Component
public class Turnstile {

    private static final Logger log = LoggerFactory.getLogger(Turnstile.class);

    /** Header carrying the widget token — named after Cloudflare's form field. */
    public static final String TURNSTILE_HEADER = "cf-turnstile-response";

    /** Codes that mean the deployment is misconfigured, not that the visitor failed. */
    private static final List<String> OPERATOR_FAULT_CODES = List.of(
            "missing-input-secret", "invalid-input-secret", "bad-request");

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public Turnstile(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static String unavailableMessage() {
        return "Verification is temporarily unavailable. Please try again shortly.";
    }

    private static String challengeFailedMessage() {
        return "Verification failed. Please complete the challenge again.";
    }

    /** {@code token_from_headers}: trimmed, non-empty header value or null. */
    public static String tokenFromHeaders(HttpServletRequest request) {
        String value = request.getHeader(TURNSTILE_HEADER);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * {@code verify_request}: a no-op when Turnstile is not configured; once
     * configured every outcome other than Cloudflare success is an error.
     */
    public void verifyRequest(HttpServletRequest request, String remoteIp, String action) {
        String secret = properties.turnstileActiveSecret();
        if (secret == null) {
            return;
        }
        String token = tokenFromHeaders(request);
        if (token == null) {
            log.warn(
                    "Turnstile: {} request carried no {} header. If this is every request, rebuild the"
                        + " frontend image with VITE_TURNSTILE_SITE_KEY set to the same site key as the"
                        + " backend's TURNSTILE_SITE_KEY.",
                    action, TURNSTILE_HEADER);
            throw ApiError.badRequest("Please complete the verification challenge.");
        }
        if (token.length() > 2048) {
            throw ApiError.badRequest(challengeFailedMessage());
        }

        String body = "secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                + "&remoteip=" + URLEncoder.encode(remoteIp == null ? "" : remoteIp,
                        StandardCharsets.UTF_8);
        HttpRequest verify = HttpRequest.newBuilder()
                .uri(URI.create(properties.getTurnstileVerifyUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(verify, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("Turnstile siteverify request failed for {}: {}", action, e.getMessage());
            throw ApiError.serviceUnavailable(unavailableMessage());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Turnstile siteverify returned {} for {}: {}",
                    response.statusCode(), action, response.body());
            throw ApiError.serviceUnavailable(unavailableMessage());
        }

        JsonNode verdict;
        try {
            verdict = objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("Turnstile siteverify decode failed for {}: {}", action, e.getMessage());
            throw ApiError.serviceUnavailable(unavailableMessage());
        }
        if (verdict.path("success").asBoolean(false)) {
            return;
        }
        List<String> errorCodes = new java.util.ArrayList<>();
        for (JsonNode code : verdict.path("error-codes")) {
            errorCodes.add(code.asText());
        }
        classifyFailure(errorCodes, action);
    }

    /**
     * {@code classify_failure}: operator faults (misconfigured secret) → 503
     * logged loudly; visitor faults (failed/replayed challenge) → 400.
     */
    public static void classifyFailure(List<String> errorCodes, String action) {
        boolean operatorFault = errorCodes.stream().anyMatch(OPERATOR_FAULT_CODES::contains);
        if (operatorFault) {
            log.error(
                    "Turnstile is misconfigured — siteverify rejected the secret key for {}: {}."
                        + " Check TURNSTILE_SECRET_KEY; it must be the SECRET half from the Cloudflare"
                        + " dashboard, not a second copy of the site key.",
                    action, String.join(", ", errorCodes));
            throw ApiError.serviceUnavailable(unavailableMessage());
        }
        log.warn("Turnstile challenge rejected for {}: {}",
                action, errorCodes.isEmpty() ? "no error code" : String.join(", ", errorCodes));
        throw ApiError.badRequest(challengeFailedMessage());
    }
}
