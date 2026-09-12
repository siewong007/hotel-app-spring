package com.hotelapp.portal;

import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of {@code services/paypal_client.rs} — a minimal Orders v2 client over
 * {@link HttpClient}. Disabled (503) unless enabled AND fully credentialed;
 * OAuth tokens are cached process-wide with a two-minute safety margin.
 */
@Component
public class PaypalClient {

    private static final Logger log = LoggerFactory.getLogger(PaypalClient.class);

    public record PaypalCaptureOutcome(
            String status,
            String customId,
            String capturedAmount,
            String capturedCurrency) {
    }

    private record CachedToken(String token, Instant expiresAt) {
    }

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();
    private volatile CachedToken tokenCache;

    public PaypalClient(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return props.isPaypalConfigured();
    }

    private String apiBase() {
        String base = props.getPaypalApiBase();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private void requireConfigured() {
        if (!props.isPaypalConfigured()) {
            throw ApiError.serviceUnavailable("PayPal payments are not configured.");
        }
    }

    private String getAccessToken() {
        CachedToken cached = tokenCache;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.token();
        }

        String basic = Base64.getEncoder().encodeToString(
                (nullToEmpty(props.getPaypalClientId()) + ":"
                        + nullToEmpty(props.getPaypalClientSecret()))
                        .getBytes(StandardCharsets.UTF_8));
        JsonNode json = send(HttpRequest.newBuilder()
                        .uri(URI.create(apiBase() + "/v1/oauth2/token"))
                        .header("Authorization", "Basic " + basic)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                        .timeout(Duration.ofSeconds(20))
                        .build(),
                "PayPal is unreachable.", "PayPal authentication failed.");

        String token = textOrNull(json.get("access_token"));
        if (token == null) {
            throw ApiError.serviceUnavailable("PayPal token response missing access_token.");
        }
        long expiresIn = json.path("expires_in").asLong(0);
        if (expiresIn > 300) {
            tokenCache = new CachedToken(token,
                    Instant.now().plusSeconds(expiresIn - 120));
        }
        return token;
    }

    /** Create an Orders v2 order (intent CAPTURE); returns the PayPal order id. */
    public String createOrder(BigDecimal amount, String currency, String customId) {
        requireConfigured();
        String token = getAccessToken();
        String amountStr = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        String payload = """
                {"intent":"CAPTURE","purchase_units":[{"custom_id":"%s",
                "amount":{"currency_code":"%s","value":"%s"}}]}
                """.formatted(escapeJson(customId), escapeJson(currency), amountStr);

        JsonNode json = send(HttpRequest.newBuilder()
                        .uri(URI.create(apiBase() + "/v2/checkout/orders"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .timeout(Duration.ofSeconds(20))
                        .build(),
                "PayPal create-order failed.", "PayPal could not create the order.");

        String orderId = textOrNull(json.get("id"));
        if (orderId == null) {
            throw ApiError.serviceUnavailable("PayPal order response missing id.");
        }
        return orderId;
    }

    /** Capture a previously created order; parses status + custom_id + amount. */
    public PaypalCaptureOutcome captureOrder(String orderId) {
        requireConfigured();
        String token = getAccessToken();
        JsonNode json = send(HttpRequest.newBuilder()
                        .uri(URI.create(apiBase() + "/v2/checkout/orders/" + orderId + "/capture"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .timeout(Duration.ofSeconds(20))
                        .build(),
                "PayPal capture failed.", "PayPal could not capture the order.");

        String status = textOrNull(json.get("status"));
        if (status == null) {
            status = "UNKNOWN";
        }

        JsonNode capture = json.path("purchase_units").path(0).path("payments")
                .path("captures").path(0);
        String customId = textOrNull(capture.get("custom_id"));
        if (customId == null) {
            customId = textOrNull(json.path("purchase_units").path(0).get("custom_id"));
        }
        String capturedAmount = textOrNull(capture.path("amount").get("value"));
        String capturedCurrency = textOrNull(capture.path("amount").get("currency_code"));
        return new PaypalCaptureOutcome(status, customId, capturedAmount, capturedCurrency);
    }

    private JsonNode send(HttpRequest request, String transportMessage, String failureMessage) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("PayPal request failed: {}", e.getMessage());
            throw ApiError.serviceUnavailable(transportMessage);
        }
        String body = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("PayPal call failed ({}): {}", response.statusCode(), body);
            throw ApiError.serviceUnavailable(failureMessage);
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            log.error("PayPal decode failed: {}", e.getMessage());
            throw ApiError.serviceUnavailable("PayPal returned an unreadable response.");
        }
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isString() ? node.asString() : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
