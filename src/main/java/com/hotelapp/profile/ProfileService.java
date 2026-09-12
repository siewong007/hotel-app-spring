package com.hotelapp.profile;

import com.hotelapp.auth.AuthService;
import com.hotelapp.auth.GoogleIdentityService;
import com.hotelapp.auth.PasskeyService;
import com.hotelapp.auth.TotpSecrets;
import com.hotelapp.auth.TotpVerifier;
import com.hotelapp.auth.dto.AuthDtos.CompleteGuestProfileRequest;
import com.hotelapp.auth.dto.AuthDtos.PasswordUpdateInput;
import com.hotelapp.auth.dto.AuthDtos.UserProfile;
import com.hotelapp.auth.dto.AuthDtos.UserProfileUpdate;
import com.hotelapp.auth.dto.AuthDtos.UserSessionInfo;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.email.AccountEmails;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Port of {@code services/profile.rs}: profile get/update/complete/password,
 * sessions list with masked IPs and timezone-derived locations, and session
 * revocation.
 */
@Service
public class ProfileService {

    /** The reserved non-deliverable domain for accounts without a real email. */
    private static final String UNCONFIGURED_EMAIL_SUFFIX = "@no-email.invalid";

    private final JdbcTemplate jdbc;
    private final AuthService authService;
    private final PasskeyService passkeyService;
    private final AccountEmails accountEmails;
    private final AuditWriter audit;
    private final TransactionTemplate tx;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    public ProfileService(JdbcTemplate jdbc, AuthService authService,
            PasskeyService passkeyService, AccountEmails accountEmails,
            AuditWriter audit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.passkeyService = passkeyService;
        this.accountEmails = accountEmails;
        this.audit = audit;
        this.tx = tx;
    }

    // ---- profile -------------------------------------------------------------

    /** {@code get_user_profile}: the profile row + the completion verdict. */
    public UserProfile getUserProfile(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, username,
                       CASE WHEN email LIKE '%@no-email.invalid' THEN '' ELSE email END AS email,
                       CASE WHEN email LIKE '%@no-email.invalid' THEN false ELSE true END AS email_configured,
                       is_verified, user_type, full_name, phone, avatar_url,
                       created_at, updated_at, last_login_at
                FROM users
                WHERE id = ? AND is_active = true AND deleted_at IS NULL
                """, userId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("User not found");
        }
        return toProfile(rows.get(0), userId);
    }

    private UserProfile toProfile(Map<String, Object> row, long userId) {
        var completion = authService.profileCompletionForUser(userId);
        return new UserProfile(
                ((Number) row.get("id")).longValue(),
                (String) row.get("username"),
                (String) row.get("email"),
                Boolean.TRUE.equals(row.get("email_configured")),
                Boolean.TRUE.equals(row.get("is_verified")),
                (String) row.get("user_type"),
                (String) row.get("full_name"),
                (String) row.get("phone"),
                (String) row.get("avatar_url"),
                toInstant(row.get("created_at")),
                toInstant(row.get("updated_at")),
                toInstant(row.get("last_login_at")),
                completion.complete(),
                completion.missingFields());
    }

    /**
     * {@code update_user_profile}: sanitize first, then per-field writes. Guest
     * accounts may only configure an email once (no-email.invalid placeholder
     * counts as unconfigured) — afterwards the address is immutable here.
     */
    public UserProfile updateUserProfile(long userId, UserProfileUpdate input) {
        String fullName = input.fullName() == null ? null
                : Sanitizer.sanitizeGuestName(input.fullName());
        String email = input.email() == null ? null
                : Sanitizer.sanitizeEmail(input.email());
        String phone = input.phone() == null ? null
                : Sanitizer.sanitizePhone(input.phone());
        String avatarUrl = input.avatarUrl() == null ? null
                : sanitizeUrl(input.avatarUrl());

        if (fullName != null) {
            if (fullName.isEmpty() || fullName.length() > 100) {
                throw ApiError.badRequest(
                        "Full name must be between 1 and 100 characters");
            }
            jdbc.update("UPDATE users SET full_name = ?, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE id = ?", fullName, userId);
        }
        if (email != null) {
            if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw ApiError.badRequest("Invalid email format");
            }
            Map<String, Object> account = findActiveUser(userId);
            String currentEmail = (String) account.get("email");
            boolean emailChanged = !email.equalsIgnoreCase(currentEmail);
            boolean isGuest = "guest".equals(account.get("user_type"));
            boolean emailConfigured = currentEmail != null
                    && !currentEmail.toLowerCase().endsWith(UNCONFIGURED_EMAIL_SUFFIX);

            if (isGuest && emailChanged) {
                if (emailConfigured) {
                    throw ApiError.badRequest(
                            "Your email is already configured and cannot be changed here");
                }
                if (emailExistsForOtherUser(userId, email)) {
                    throw ApiError.conflict("An account with this email already exists");
                }
                boolean configured = configureGuestEmail(userId, email);
                if (!configured) {
                    throw ApiError.conflict("Email has already been configured");
                }
                // The address is now on the account and unverified, so login
                // will refuse it until the guest clicks the link — sending it
                // is not optional.
                accountEmails.trySendEmailVerification(userId);
            } else if (emailChanged) {
                jdbc.update("UPDATE users SET email = ?, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE id = ?", email, userId);
            }
        }
        if (phone != null) {
            if (phone.length() > 30) {
                throw ApiError.badRequest("Phone number is too long");
            }
            jdbc.update("UPDATE users SET phone = ?, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE id = ?", phone, userId);
        }
        if (avatarUrl != null) {
            if (avatarUrl.length() > 2048) {
                throw ApiError.badRequest("Avatar URL is too long");
            }
            jdbc.update("UPDATE users SET avatar_url = ?, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE id = ?", avatarUrl, userId);
        }

        return getUserProfile(userId);
    }

    /**
     * {@code update_password}: current-password verify → new hash → revoke all
     * sessions + passkeys (a passkey satisfies 2FA on its own and would
     * otherwise survive the rotation).
     */
    public void updatePassword(long userId, PasswordUpdateInput input) {
        if (input.currentPassword() == null || input.currentPassword().isBlank()
                || input.currentPassword().length() > 128) {
            throw ApiError.badRequest("Current password is required");
        }
        if (input.newPassword() == null || input.newPassword().length() < 8
                || input.newPassword().length() > 128) {
            throw ApiError.badRequest(
                    "New password must be between 8 and 128 characters");
        }
        AuthService.validatePassword(input.newPassword());

        String currentHash;
        try {
            currentHash = jdbc.queryForObject(
                    "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            throw ApiError.unauthorized("Current password is incorrect");
        }
        if (currentHash == null || !bcrypt.matches(input.currentPassword(), currentHash)) {
            throw ApiError.unauthorized("Current password is incorrect");
        }

        jdbc.update("UPDATE users SET password_hash = ?, password_changed_at = CURRENT_TIMESTAMP,"
                + " updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                bcrypt.encode(input.newPassword()), userId);
        authService.revokeAllUserTokens(userId);
        int revokedPasskeys = passkeyService.revokeAllForUser(userId);
        audit.event(userId, "password_changed", "user", userId, null);
        if (revokedPasskeys > 0) {
            audit.event(userId, "passkeys_revoked_by_password_change", "user", userId,
                    Map.of("revoked", revokedPasskeys));
        }
    }

    /**
     * {@code complete_guest_profile}: the Google-guest profile-completion write
     * path. Guest-only — non-guest accounts have nothing to complete.
     */
    public UserProfile completeGuestProfile(long userId, CompleteGuestProfileRequest input) {
        String firstName = input.firstName() == null ? null
                : Sanitizer.sanitizeGuestName(input.firstName());
        String lastName = input.lastName() == null ? null
                : Sanitizer.sanitizeGuestName(input.lastName());
        String phone = input.phone() == null ? null
                : Sanitizer.sanitizePhone(input.phone());
        String addressLine1 = input.addressLine1() == null ? null
                : Sanitizer.sanitizeText(input.addressLine1().trim());
        if (addressLine1 != null && addressLine1.isEmpty()) {
            addressLine1 = null;
        }

        if (firstName == null || firstName.isEmpty() || firstName.length() > 50) {
            throw ApiError.badRequest("First name must be at most 50 characters");
        }
        if (lastName == null || lastName.isEmpty() || lastName.length() > 50) {
            throw ApiError.badRequest("Last name must be at most 50 characters");
        }
        if (phone == null || phone.isEmpty()) {
            throw ApiError.badRequest("invalid_phone");
        }
        String digits = phone.startsWith("+") ? phone.substring(1) : phone;
        if (digits.length() < 8 || digits.length() > 15
                || !digits.chars().allMatch(Character::isDigit)) {
            throw ApiError.badRequest("invalid_phone");
        }
        if (addressLine1 != null && addressLine1.length() > 255) {
            throw ApiError.badRequest("Address is too long");
        }

        Long guestId;
        try {
            guestId = jdbc.query(
                    "SELECT guest_id FROM users WHERE id = ? AND deleted_at IS NULL",
                    rs -> rs.next() ? (rs.getObject("guest_id") == null
                            ? null : rs.getLong("guest_id")) : null,
                    userId);
        } catch (Exception e) {
            guestId = null;
        }
        if (guestId == null) {
            throw ApiError.forbidden("Guest account required");
        }

        String fullName = firstName + " " + lastName;
        if (nickNameConflictId(fullName, guestId) != null) {
            throw ApiError.conflict(
                    "A guest profile with this name already exists. Please sign in with your"
                            + " existing account or contact the hotel for help.");
        }

        String finalPhone = phone;
        String finalAddress = addressLine1;
        String finalFirst = firstName;
        String finalLast = lastName;
        long gid = guestId;
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                    UPDATE guests
                    SET first_name = ?, last_name = ?, nick_name = ?, phone = ?,
                        address_line_1 = COALESCE(?, address_line_1),
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, finalFirst, finalLast, fullName, finalPhone, finalAddress, gid);
            jdbc.update("UPDATE users SET full_name = ?, phone = ?,"
                    + " updated_at = CURRENT_TIMESTAMP WHERE id = ?", fullName, finalPhone,
                    userId);
        });

        audit.event(userId, "guest_profile_completed", "user", userId,
                Map.of("guest_id", guestId));
        return getUserProfile(userId);
    }

    // ---- sessions -------------------------------------------------------------

    /**
     * {@code list_sessions}: active sessions with masked IPs, the timezone
     * behind each sign-in, and the coarse place label the UI shows.
     */
    public List<UserSessionInfo> listSessions(long userId, String currentSessionId) {
        List<Map<String, Object>> sessions = authService.listActiveSessions(userId);
        return sessions.stream()
                .map(session -> new UserSessionInfo(
                        String.valueOf(session.get("id")),
                        (String) session.get("user_agent"),
                        session.get("ip_address") == null ? null
                                : maskIpAddress((String) session.get("ip_address")),
                        toInstant(session.get("created_at")),
                        toInstant(session.get("last_used_at")),
                        toInstant(session.get("expires_at")),
                        currentSessionId != null
                                && currentSessionId.equals(String.valueOf(session.get("id"))),
                        session.get("client_timezone") == null ? null
                                : locationFromTimezone((String) session.get("client_timezone")),
                        (String) session.get("client_timezone")))
                .toList();
    }

    /**
     * {@code revoke_session}: the guarded update only touches an active session
     * owned by the caller — nothing to revoke is a 404, not silent success.
     */
    public void revokeSession(long userId, String sessionId) {
        if (!authService.revokeUserSession(userId, sessionId)) {
            throw ApiError.notFound("Active session not found");
        }
        audit.event(userId, "session_revoked", "user", userId,
                Map.of("session_id", sessionId));
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * {@code location_from_timezone}: the city segment of an IANA zone —
     * `Asia/Kuala_Lumpur` → "Kuala Lumpur". `Etc/*` and bare `UTC` name no
     * place, so they map to nothing rather than showing "GMT+8" as a location.
     */
    public static String locationFromTimezone(String timezone) {
        int slash = timezone.lastIndexOf('/');
        String city = slash >= 0 ? timezone.substring(slash + 1).trim() : timezone.trim();
        if (city.isEmpty()) {
            return null;
        }
        if (timezone.startsWith("Etc/") || city.equalsIgnoreCase("UTC")) {
            return null;
        }
        return city.replace('_', ' ');
    }

    /** {@code mask_ip_address}: last octet of IPv4, last segment of IPv6. */
    public static String maskIpAddress(String ip) {
        int dot = ip.lastIndexOf('.');
        if (dot >= 0) {
            return ip.substring(0, dot) + ".•••";
        }
        int colon = ip.lastIndexOf(':');
        if (colon >= 0) {
            return ip.substring(0, colon) + ":••••";
        }
        return "•••";
    }

    private Map<String, Object> findActiveUser(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, email, user_type FROM users WHERE id = ? AND deleted_at IS NULL",
                userId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("User not found");
        }
        return rows.get(0);
    }

    private boolean emailExistsForOtherUser(long userId, String email) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)"
                        + " AND id <> ?)",
                Boolean.class, email, userId));
    }

    /**
     * {@code configure_guest_email}: attach the address, mark it unverified,
     * clear any stale token columns, then mirror onto the guest row.
     */
    private boolean configureGuestEmail(long userId, String email) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            int updated = jdbc.update("""
                    UPDATE users SET email = ?, is_verified = false,
                        email_verification_token = NULL, email_token_expires_at = NULL,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND user_type = 'guest' AND email LIKE ?
                    """, email, userId, "%" + UNCONFIGURED_EMAIL_SUFFIX);
            if (updated == 0) {
                return false;
            }
            jdbc.update("""
                    UPDATE guests SET email = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = (SELECT guest_id FROM users WHERE id = ?)
                    """, email, userId);
            return true;
        }));
    }

    /** The guest's nick_name conflict probe (case-insensitive, trim). */
    private Long nickNameConflictId(String fullName, Long excludeGuestId) {
        List<Long> rows = jdbc.queryForList("""
                SELECT id FROM guests
                WHERE deleted_at IS NULL AND lower(trim(nick_name)) = lower(trim(?))
                  AND (?::bigint IS NULL OR id <> ?::bigint)
                LIMIT 1
                """, Long.class, fullName, excludeGuestId, excludeGuestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code sanitize_url}: http/https/mailto pass; bare hosts gain https. */
    private static String sanitizeUrl(String input) {
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("mailto:")) {
            return trimmed;
        }
        if (lower.startsWith("//")) {
            return null;
        }
        if (!trimmed.contains(":")) {
            return "https://" + trimmed;
        }
        return null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return null;
    }
}
