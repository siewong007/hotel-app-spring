package com.hotelapp.portal;

import com.hotelapp.auth.AuthService;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.core.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of the guest-portal auth plumbing from
 * {@code services/guest_portal.rs} (session mint/resolve/logout, hashed
 * booking access tokens) and the token/rate-limit gate helpers in
 * {@code routes/guest_portal.rs}.
 *
 * Raw session tokens are never persisted — only their SHA-256 hashes.
 * Booking access tokens (the pre-check-in link secret) are stored as
 * {@code sha256:<hash>} with a legacy plaintext read for pre-cutover rows.
 */
@Component
public class PortalAuth {

    /** Mirrors BOOKING_ACCESS_TOKEN_HASH_PREFIX. */
    public static final String BOOKING_ACCESS_TOKEN_HASH_PREFIX = "sha256:";

    /** Mirrors BOOKING_ACCESS_TOKEN_HEADER. */
    public static final String BOOKING_ACCESS_TOKEN_HEADER = "x-booking-access-token";

    private static final SecureRandom RNG = new SecureRandom();
    private static final long SESSION_TTL_HOURS = 24;

    private final JdbcTemplate jdbc;
    private final RateLimitService rateLimits;
    private final ClientIp clientIp;

    public PortalAuth(JdbcTemplate jdbc, RateLimitService rateLimits, ClientIp clientIp) {
        this.jdbc = jdbc;
        this.rateLimits = rateLimits;
        this.clientIp = clientIp;
    }

    /** 256-bit random token, hex-encoded — generate_session_token. */
    public static String generateSessionToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** SHA-256 hash of a token, hex-encoded (matches the refresh-token scheme). */
    public static String hashSessionToken(String token) {
        return AuthService.sha256Hex(token);
    }

    /** Value stored in bookings.pre_checkin_token for a newly issued token. */
    public static String persistBookingAccessToken(String token) {
        return BOOKING_ACCESS_TOKEN_HASH_PREFIX + hashSessionToken(token);
    }

    /**
     * Whether {@code presented} authenticates against the stored
     * {@code pre_checkin_token} value: prefixed rows match the prefixed hash,
     * pre-cutover rows match the raw token.
     */
    public static boolean bookingAccessTokenMatches(String presented, String stored) {
        if (stored.startsWith(BOOKING_ACCESS_TOKEN_HASH_PREFIX)) {
            return stored.equals(persistBookingAccessToken(presented));
        }
        return stored.equals(presented);
    }

    public String clientIp(HttpServletRequest request) {
        return clientIp.extract(request);
    }

    public String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    // ------------------------------------------------------------------
    // Guest session (bearer) resolution
    // ------------------------------------------------------------------

    /** Extract the raw Bearer token, or 401. */
    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw ApiError.unauthorized("Missing guest session token");
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw ApiError.unauthorized("Missing guest session token");
        }
        return token;
    }

    /**
     * Resolve an unexpired session by its raw token, bumping last_used_at.
     * Returns the guest id. {@code require_guest_session_token} upstream.
     */
    public Long requireGuestSessionToken(String token) {
        String tokenHash = hashSessionToken(token);
        Long guestId = jdbc.query(
                "SELECT guest_id FROM guest_portal_sessions "
                        + "WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP LIMIT 1",
                rs -> rs.next() ? rs.getLong(1) : null,
                tokenHash);
        if (guestId == null) {
            throw ApiError.unauthorized("Invalid or expired guest session");
        }
        // Best-effort last_used bookkeeping; a failure must not block reads.
        try {
            jdbc.update("UPDATE guest_portal_sessions SET last_used_at = CURRENT_TIMESTAMP "
                    + "WHERE token_hash = ?", tokenHash);
        } catch (Exception ignored) {
            // deliberately swallowed — upstream does `let _ =`
        }
        return guestId;
    }

    /** {@code require_guest_session}: bearer token -> guest id. */
    public Long requireGuestSession(HttpServletRequest request) {
        return requireGuestSessionToken(bearerToken(request));
    }

    /**
     * {@code require_guest_session_for_read}: resolve the session and apply the
     * shared per-session read budget (keyed "guest:{id}").
     */
    public Long requireGuestSessionForRead(HttpServletRequest request) {
        Long guestId = requireGuestSession(request);
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_TOKEN_READ, "guest:" + guestId);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many portal requests. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
        return guestId;
    }

    // ------------------------------------------------------------------
    // Booking access token resolution + token/IP rate limits
    // ------------------------------------------------------------------

    /**
     * Shape-check an unauthenticated portal token BEFORE it reaches the keyed
     * rate limiter, so junk values cannot allocate limiter entries.
     * {@code ensure_plausible_portal_token} upstream.
     */
    public static void ensurePlausiblePortalToken(String token) {
        boolean plausible = !token.isEmpty()
                && token.length() <= 128
                && token.chars().allMatch(c ->
                        (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                                || (c >= 'A' && c <= 'F') || c == '-');
        if (!plausible) {
            throw ApiError.badRequest("This booking link is invalid.");
        }
    }

    /**
     * Header-first resolution so the token need not appear in the URL; path
     * tokens stay accepted for already-issued links.
     * {@code resolve_booking_access_token} upstream.
     */
    public static String resolveBookingAccessToken(HttpServletRequest request, String pathToken) {
        String fromHeader = request.getHeader(BOOKING_ACCESS_TOKEN_HEADER);
        if (fromHeader != null) {
            fromHeader = fromHeader.trim();
            if (fromHeader.isEmpty()) {
                fromHeader = null;
            }
        }
        String token = fromHeader != null ? fromHeader
                : (pathToken != null && !pathToken.trim().isEmpty() ? pathToken.trim() : null);
        if (token == null) {
            throw ApiError.unauthorized("Missing booking access token");
        }
        ensurePlausiblePortalToken(token);
        return token;
    }

    private void checkTokenIpLimit(HttpServletRequest request) {
        String ip = clientIp.extract(request);
        if (!rateLimits.check(RateLimitService.Category.GUEST_PORTAL_TOKEN_IP, ip).allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many requests. Please try again later.", 900);
        }
    }

    /** {@code require_booking_token_for_read} upstream. */
    public String requireBookingTokenForRead(HttpServletRequest request, String pathToken) {
        String token = resolveBookingAccessToken(request, pathToken);
        checkTokenIpLimit(request);
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_TOKEN_READ, token);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many requests for this booking link. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
        return token;
    }

    /** {@code require_booking_token_for_write} upstream. */
    public String requireBookingTokenForWrite(
            HttpServletRequest request, String pathToken, String retryMessage) {
        String token = resolveBookingAccessToken(request, pathToken);
        checkTokenIpLimit(request);
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_TOKEN, token);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    retryMessage + " " + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
        return token;
    }

    /** {@code require_payment_booking_token} upstream (100/10min per link). */
    public String requirePaymentBookingToken(HttpServletRequest request, String pathToken) {
        String token = resolveBookingAccessToken(request, pathToken);
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_TOKEN_PAYMENT, token);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many payment attempts for this booking. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
        return token;
    }

    /** Per-guest payment limit on the authenticated routes ({@code check_guest_payment_rate_limit}). */
    public void checkGuestPaymentRateLimit(long guestId) {
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_PAYMENT, String.valueOf(guestId));
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many payment attempts. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
    }

    public RateLimitService rateLimits() {
        return rateLimits;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public static long sessionTtlHours() {
        return SESSION_TTL_HOURS;
    }
}
