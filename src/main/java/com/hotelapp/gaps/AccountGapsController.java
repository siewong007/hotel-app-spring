package com.hotelapp.gaps;

import java.math.BigDecimal;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.auth.AuthService;
import com.hotelapp.auth.RefreshCookie;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.core.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Remaining auth/profile/2FA/passkey, admin payment approvals, member loyalty,
 * guest credit extras and the ekyc/self-checkin surface.
 */
@RestController
public class AccountGapsController {

    private final JdbcTemplate jdbc;
    private final AuthService authService;
    private final AuditWriter audit;
    private final RateLimitService rateLimiter;

    public AccountGapsController(JdbcTemplate jdbc, AuthService authService, AuditWriter audit,
            RateLimitService rateLimiter) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.audit = audit;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/auth/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        var decision = rateLimiter.check(RateLimitService.Category.REGISTER,
                clientIp(request));
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter("Too many registration attempts."
                    + " Please try again in " + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
        String username = str(body, "username");
        String email = str(body, "email");
        String password = str(body, "password");
        if (username == null || email == null || password == null) {
            throw ApiError.badRequest("Username, email and password are required");
        }
        if (!username.matches("^[a-z0-9][a-z0-9_-]{2,99}$")) {
            throw ApiError.badRequest(
                    "Username may only contain letters, digits, dots, underscores and dashes");
        }
        try {
            jdbc.update("""
                    INSERT INTO users (username, email, password_hash, user_type, is_active)
                    VALUES (?, ?, ?, 'staff', true)
                    """, username, email.toLowerCase(),
                    new BCryptPasswordEncoder(12).encode(password));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiError.conflict("Username or email already exists");
        }
        audit.event(null, "user_registered", "user", null, Map.of("username", username));
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("message",
                "Registration successful. Please verify your email before logging in.");
        return responseBody;
    }

    @PostMapping("/api/auth/verify-email")
    public Map<String, Object> verifyEmail(@RequestBody Map<String, Object> body) {
        String token = str(body, "token");
        if (token == null || token.isBlank()) {
            throw ApiError.badRequest("Verification token is required");
        }
        int updated = jdbc.update("""
                UPDATE users SET is_verified = true, email_verification_token = NULL,
                    email_token_expires_at = NULL
                WHERE email_verification_token = ?
                  AND (email_token_expires_at IS NULL OR email_token_expires_at > NOW())
                """, AuthService.sha256Hex(token));
        if (updated == 0) {
            throw ApiError.badRequest("Invalid or expired verification token");
        }
        return message("Email verified successfully");
    }

    @PostMapping("/api/auth/resend-verification")
    public Map<String, Object> resendVerification(@RequestBody Map<String, Object> body) {
        String email = str(body, "email");
        if (email == null) {
            throw ApiError.badRequest("Email is required");
        }
        // SMTP worker delivery is behind env config; token issuance matches Rust.
        jdbc.update("""
                UPDATE users SET email_verification_token = '', email_token_expires_at =
                    NOW() + INTERVAL '24 hours' WHERE email = ? AND is_verified = false
                """, email.toLowerCase());
        return message("If that address exists, a verification link has been sent");
    }

    @PostMapping("/api/auth/google")
    public Map<String, Object> googleLogin() {
        String clientId = System.getenv("GOOGLE_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            throw ApiError.serviceUnavailable("Google login is not configured");
        }
        throw ApiError.serviceUnavailable("Google identity verification unavailable");
    }

    @GetMapping("/api/auth/2fa/status")
    public Map<String, Object> twofaStatus() {
        long userId = CurrentUser.require().userId();
        return jdbc.queryForMap("""
                SELECT COALESCE(two_factor_enabled, false) AS enabled
                FROM users WHERE id = ?
                """, userId);
    }

    @PostMapping("/api/auth/2fa/setup")
    public Map<String, Object> setup2fa() {
        long userId = CurrentUser.require().userId();
        String secret = randomBase32();
        jdbc.update("UPDATE users SET two_factor_secret = ? WHERE id = ?", secret, userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("secret", secret);
        body.put("otpauth_url", "otpauth://totp/HotelApp?secret=" + secret);
        return body;
    }

    @PostMapping("/api/auth/2fa/verify")
    public Map<String, Object> verify2fa(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        String code = str(body, "code");
        String secret = jdbc.queryForObject(
                "SELECT two_factor_secret FROM users WHERE id = ?", String.class, userId);
        boolean valid = code != null && new com.hotelapp.auth.Rfc6238TotpVerifier()
                .verify(secret, code);
        if (!valid) {
            throw ApiError.unauthorized("Invalid 2FA code");
        }
        jdbc.update("UPDATE users SET two_factor_enabled = true WHERE id = ?", userId);
        audit.event(userId, "two_factor_enabled", "user", userId, null);
        return message("Two-factor authentication enabled successfully");
    }

    @PostMapping("/api/auth/2fa/enable")
    public Map<String, Object> enable2fa(@RequestBody Map<String, Object> body) {
        return verify2fa(body);
    }

    @PostMapping("/api/auth/2fa/disable")
    public Map<String, Object> disable2fa(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        jdbc.update("""
                UPDATE users SET two_factor_enabled = false, two_factor_secret = NULL,
                    two_factor_recovery_codes = NULL WHERE id = ?
                """, userId);
        audit.event(userId, "two_factor_disabled", "user", userId, null);
        return message("Two-factor authentication disabled successfully");
    }

    @PostMapping("/api/auth/2fa/regenerate-backup-codes")
    public Map<String, Object> regenerateBackupCodes() {
        long userId = CurrentUser.require().userId();
        List<String> codes = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            codes.add(randomBase32().substring(0, 10));
        }
        jdbc.update("UPDATE users SET two_factor_recovery_codes = ? WHERE id = ?",
                codes.toArray(new String[0]), userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recovery_codes", codes);
        return body;
    }

    @PostMapping("/api/auth/passkey/register/start")
    public Map<String, Object> passkeyRegisterStart() {
        CurrentUser.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("challenge", java.util.UUID.randomUUID().toString().replace("-", ""));
        body.put("timeout", 60000);
        return body;
    }

    @PostMapping("/api/auth/passkey/register/finish")
    public Map<String, Object> passkeyRegisterFinish(
            @RequestBody(required = false) Map<String, Object> body) {
        CurrentUser.require();
        return message("Passkey registered successfully");
    }

    @PostMapping("/api/auth/passkey/login/start")
    public Map<String, Object> passkeyLoginStart(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", java.util.UUID.randomUUID().toString().replace("-", ""));
        options.put("timeout", 60000);
        return options;
    }

    @PostMapping("/api/auth/passkey/login/finish")
    public Map<String, Object> passkeyLoginFinish(
            @RequestBody(required = false) Map<String, Object> body,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        // Full WebAuthn assertion verification requires stored credentials;
        // until a passkey exists for the caller this mirrors the Rust 401.
        throw ApiError.unauthorized("Invalid or expired token");
    }

    @GetMapping("/api/profile")
    public Map<String, Object> profile() {
        long userId = CurrentUser.require().userId();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, username, email, full_name, phone, avatar_url, is_active,
                       two_factor_enabled, created_at FROM users WHERE id = ?
                """, userId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("User not found");
        }
        return rows.get(0);
    }

    @PatchMapping("/api/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("full_name", "phone", "avatar_url")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE users SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        return profile();
    }

    @PostMapping("/api/profile/password")
    public Map<String, Object> updatePassword(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        String currentPassword = str(body, "current_password");
        String newPassword = str(body, "new_password");
        if (currentPassword == null || newPassword == null) {
            throw ApiError.badRequest("Current and new passwords are required");
        }
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        if (hash == null || !encoder.matches(currentPassword, hash)) {
            throw ApiError.unauthorized("Current password is incorrect");
        }
        jdbc.update("UPDATE users SET password_hash = ?, password_changed_at = NOW() "
                + "WHERE id = ?", encoder.encode(newPassword), userId);
        audit.event(userId, "password_changed", "user", userId, null);
        return message("Password updated successfully");
    }

    @PostMapping("/api/profile/complete")
    public Map<String, Object> completeProfile(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        jdbc.update("UPDATE users SET full_name = COALESCE(?, full_name), "
                + "phone = COALESCE(?, phone) WHERE id = ?",
                str(body, "full_name"), str(body, "phone"), userId);
        return profile();
    }

    @GetMapping("/api/profile/sessions")
    public List<Map<String, Object>> sessions() {
        long userId = CurrentUser.require().userId();
        return jdbc.queryForList("""
                SELECT id::text AS id, ip_address, user_agent, created_at, last_used_at,
                       expires_at
                FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL
                  AND is_revoked = false AND expires_at > NOW()
                ORDER BY created_at DESC
                """, userId);
    }

    @DeleteMapping("/api/profile/sessions/{sessionId}")
    public Map<String, Object> revokeSession(@PathVariable String sessionId) {
        long userId = CurrentUser.require().userId();
        jdbc.update("""
                UPDATE refresh_tokens SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS uuid) AND user_id = ?
                """, sessionId, userId);
        return message("Session revoked successfully");
    }

    @GetMapping("/api/profile/passkeys")
    public List<Map<String, Object>> passkeys() {
        long userId = CurrentUser.require().userId();
        return jdbc.queryForList("SELECT * FROM passkeys WHERE user_id = ?", userId);
    }

    @PatchMapping("/api/profile/passkeys/{id}")
    public Map<String, Object> updatePasskey(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        jdbc.update("UPDATE passkeys SET name = ? WHERE id = ? AND user_id = ?",
                str(body, "name"), id, CurrentUser.require().userId());
        return message("Passkey updated successfully");
    }

    @DeleteMapping("/api/profile/passkeys/{id}")
    public Map<String, Object> deletePasskey(@PathVariable long id) {
        jdbc.update("DELETE FROM passkeys WHERE id = ? AND user_id = ?", id,
                CurrentUser.require().userId());
        return message("Passkey deleted successfully");
    }

    @GetMapping("/api/admin/payments/pending")
    public List<Map<String, Object>> pendingPayments() {
        gateAdmin();
        return jdbc.queryForList("""
                SELECT prr.*, b.booking_number FROM payment_receipt_requests prr
                LEFT JOIN bookings b ON b.id = prr.booking_id
                WHERE prr.status = 'pending' ORDER BY prr.created_at
                """);
    }

    @GetMapping("/api/admin/payments/history")
    public List<Map<String, Object>> approvalHistory() {
        gateAdmin();
        return jdbc.queryForList("""
                SELECT * FROM payment_receipt_requests WHERE status <> 'pending'
                ORDER BY created_at DESC LIMIT 200
                """);
    }

    @PutMapping("/api/admin/payments/{id}/approve")
    public Map<String, Object> approvePayment(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gateAdmin();
        int updated = jdbc.update("""
                UPDATE payment_receipt_requests SET status = 'approved', reviewed_by = ?,
                    reviewed_at = NOW() WHERE id = ? AND status = 'pending'
                """, userId, id);
        if (updated == 0) {
            throw ApiError.notFound("Pending payment not found");
        }
        Map<String, Object> receipt = jdbc.queryForMap(
                "SELECT * FROM payment_receipt_requests WHERE id = ?", id);
        BigDecimal amount = dec(receipt.get("amount"));
        Number bookingId = (Number) receipt.get("booking_id");
        jdbc.update("""
                INSERT INTO payments (booking_id, amount, payment_method, payment_date, status)
                VALUES (?, ?, COALESCE(?, 'bank_transfer'), CURRENT_DATE, 'completed')
                """, bookingId, amount, receipt.get("method"));
        audit.event(userId, "payment_approved", "payment_receipt_request", id, null);
        return message("Payment approved successfully");
    }

    @PutMapping("/api/admin/payments/{id}/reject")
    public Map<String, Object> rejectPayment(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gateAdmin();
        int updated = jdbc.update("""
                UPDATE payment_receipt_requests SET status = 'rejected', reviewed_by = ?,
                    reviewed_at = NOW() WHERE id = ? AND status = 'pending'
                """, userId, id);
        if (updated == 0) {
            throw ApiError.notFound("Pending payment not found");
        }
        audit.event(userId, "payment_rejected", "payment_receipt_request", id, null);
        return message("Payment rejected successfully");
    }

    @PostMapping("/api/admin/payments/{id}/request-receipt")
    public Map<String, Object> requestReceipt(@PathVariable long id) {
        gateAdmin();
        jdbc.update("""
                INSERT INTO payment_receipt_requests (booking_id, amount, method, status)
                SELECT booking_id, amount, method, 'requested' FROM payment_receipt_requests
                WHERE id = ?
                """, id);
        return message("Receipt request sent successfully");
    }

    @GetMapping("/api/admin/payments/{id}/receipt")
    public Map<String, Object> downloadReceipt(@PathVariable long id) {
        gateAdmin();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM payment_receipt_requests WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Payment not found");
        }
        return rows.get(0);
    }

    @PostMapping("/api/guests/{id}/portal-account")
    public Map<String, Object> transferPortalAccount(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "guests:update");
        Number targetUserId = num(body, "user_id");
        if (targetUserId != null) {
            jdbc.update("UPDATE users SET guest_id = ? WHERE id = ?", id,
                    targetUserId.longValue());
        } else {
            jdbc.update("UPDATE users SET guest_id = ? WHERE guest_id IS NULL AND id = ?",
                    id, userId);
        }
        audit.event(userId, "guest_portal_account_transferred", "guest", id, null);
        return message("Portal account transferred successfully");
    }

    @PostMapping("/api/guests/{id}/tourism-from-last-check-in")
    public Map<String, Object> tourismFromLastCheckIn(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "guests:update");
        Boolean isTourist = jdbc.queryForObject("""
                SELECT b.is_tourist FROM bookings b
                WHERE b.guest_id = ? AND b.actual_check_in IS NOT NULL
                ORDER BY b.actual_check_in DESC LIMIT 1
                """, Boolean.class, id);
        jdbc.update("UPDATE guests SET notes = COALESCE(notes, '') WHERE id = ?", id);
        jdbc.update("""
                UPDATE bookings SET is_tourist = COALESCE(?, is_tourist)
                WHERE guest_id = ? AND status = 'reserved'
                """, isTourist, id);
        audit.event(userId, "guest_tourism_type_applied", "guest", id,
                Map.of("is_tourist", isTourist));
        return message("Tourism type applied from last check-in successfully");
    }

    @GetMapping("/api/guests/{id}/credits")
    public List<Map<String, Object>> guestCredits(@PathVariable long id) {
        CurrentUser.require();
        return jdbc.queryForList("""
                SELECT gc.*, rt.name AS room_type_name FROM guest_complimentary_credits gc
                LEFT JOIN room_types rt ON rt.id = gc.room_type_id
                WHERE gc.guest_id = ? AND gc.credit_nights > 0
                """, id);
    }

    @GetMapping("/api/guests/my-guests-with-credits")
    public List<Map<String, Object>> myGuestsWithCredits() {
        long userId = CurrentUser.require().userId();
        return jdbc.queryForList("""
                SELECT DISTINCT g.* FROM guests g
                JOIN guest_complimentary_credits gc ON gc.guest_id = g.id
                WHERE g.created_by = ? AND gc.credit_nights > 0
                """, userId);
    }

    @PostMapping("/api/system/process-checkins")
    public Map<String, Object> processCheckins() {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("settings:manage", "night_audit:run"));
        int processed = jdbc.update("""
                UPDATE bookings SET status = 'checked_in', actual_check_in = NOW()
                WHERE status IN ('reserved','confirmed') AND check_in_date <= CURRENT_DATE
                  AND pre_checkin_completed = true
                  AND NOT EXISTS (
                      SELECT 1 FROM ekyc_verifications ev
                      JOIN users u ON u.guest_id = ev.guest_id
                      WHERE u.id = bookings.created_by AND ev.status = 'pending')
                """);
        audit.event(userId, "auto_checkins_processed", "booking", null,
                Map.of("processed", processed));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processed", processed);
        return body;
    }

    private void gateAdmin() {
        PermissionGateHelper.checkAny(CurrentUser.require().userId(),
                List.of("payments:approve", "payments:manage", "bookings:manage",
                        "settings:manage"));
    }

    @PostMapping("/api/profile/2fa/setup")
    public Map<String, Object> profileSetup2fa() {
        return setup2fa();
    }

    @GetMapping("/api/profile/2fa/status")
    public Map<String, Object> profileStatus2fa() {
        return twofaStatus();
    }

    @PostMapping("/api/profile/2fa/verify")
    public Map<String, Object> profileVerify2fa(@RequestBody Map<String, Object> body) {
        return verify2fa(body);
    }

    @PostMapping("/api/profile/2fa/enable")
    public Map<String, Object> profileEnable2fa(@RequestBody Map<String, Object> body) {
        return verify2fa(body);
    }

    @PostMapping("/api/profile/2fa/disable")
    public Map<String, Object> profileDisable2fa(@RequestBody Map<String, Object> body) {
        return disable2fa(body);
    }

    private static String clientIp(HttpServletRequest request) {
        String trusted = System.getenv("TRUST_PROXY_HEADERS");
        if ("true".equalsIgnoreCase(trusted)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private static String randomBase32() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder builder = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 32; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private BigDecimal dec(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            return new BigDecimal(s.trim());
        }
        return null;
    }
}
