package com.hotelapp.auth;

import tools.jackson.databind.ObjectMapper;
import com.hotelapp.auth.dto.AccessSnapshot;
import com.hotelapp.auth.dto.AuthDtos.LoginLookupRequest;
import com.hotelapp.auth.dto.AuthDtos.LoginLookupResponse;
import com.hotelapp.auth.dto.AuthDtos.RegisterRequest;
import com.hotelapp.auth.dto.AuthResponse;
import com.hotelapp.auth.dto.LoginRequest;
import com.hotelapp.auth.dto.RefreshTokenResponse;
import com.hotelapp.auth.dto.RouteAccessPolicyDto;
import com.hotelapp.auth.dto.UserResponse;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.JwtService;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.core.text.Sanitizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    private static final long REFRESH_TTL_DAYS = 30;
    private static final long LOCKOUT_MINUTES = 30;

    /** Upstream: comma-separated role names whose members must enrol a factor. */
    private static final String REQUIRE_TWO_FACTOR_ROLES = "require_two_factor_roles";
    /** Upstream: days an in-scope account may sign in before enrolment is enforced. */
    private static final String REQUIRE_TWO_FACTOR_GRACE_DAYS = "require_two_factor_grace_days";

    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final AuthAudit audit;
    private final com.hotelapp.core.audit.AuditWriter auditWriter;
    private final TotpVerifier totpVerifier;
    private final TotpSecrets totpSecrets;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final HotelSettings settings;
    private final com.hotelapp.consent.Consents consents;
    private final com.hotelapp.communications.GuestComms guestComms;
    private final com.hotelapp.email.AccountEmails accountEmails;
    private final GoogleAuthTx googleAuthTx;
    private final GoogleIdentityService googleIdentity;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

    public AuthService(JdbcTemplate jdbc, JwtService jwtService, AuthAudit audit,
            com.hotelapp.core.audit.AuditWriter auditWriter,
            TotpVerifier totpVerifier, TotpSecrets totpSecrets, ObjectMapper objectMapper,
            AppProperties properties, HotelSettings settings,
            com.hotelapp.consent.Consents consents,
            com.hotelapp.communications.GuestComms guestComms,
            com.hotelapp.email.AccountEmails accountEmails,
            GoogleAuthTx googleAuthTx, GoogleIdentityService googleIdentity,
            TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.audit = audit;
        this.auditWriter = auditWriter;
        this.totpVerifier = totpVerifier;
        this.totpSecrets = totpSecrets;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.settings = settings;
        this.consents = consents;
        this.guestComms = guestComms;
        this.accountEmails = accountEmails;
        this.googleAuthTx = googleAuthTx;
        this.googleIdentity = googleIdentity;
        this.tx = tx;
    }

    public record LoginResult(AuthResponse response, String refreshToken) {
    }

    public record RefreshResult(RefreshTokenResponse response, String newRefreshToken) {
    }

    // ---- login lookup ------------------------------------------------------

    /**
     * {@code lookup_login_identifier}: deliberately constant for every
     * well-formed identifier — returning the real answer would let anyone
     * enumerate which usernames/emails map to active accounts. The client
     * always proceeds to the password step, where unknown identifiers fail
     * with the same generic "Invalid credentials" as a wrong password.
     */
    public LoginLookupResponse lookupLoginIdentifier(LoginLookupRequest req) {
        if (req.username() == null || req.username().trim().isEmpty()) {
            throw ApiError.badRequest("Username or email is required");
        }
        return new LoginLookupResponse(!req.username().trim().isEmpty());
    }

    // ---- password login -----------------------------------------------------

    public LoginResult login(LoginRequest request, String ip, String userAgent,
            String clientTimezone) {
        requireText(request.username(), "Username is required");
        requireText(request.password(), "Password is required");

        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT id, username, email, password_hash, full_name, phone, is_active,
                       is_verified, two_factor_enabled, two_factor_secret,
                       two_factor_recovery_codes, failed_login_attempts, locked_until,
                       created_at, updated_at
                FROM users WHERE deleted_at IS NULL AND (username = ? OR email = ?)
                """, request.username(), request.username());

        if (users.isEmpty()) {
            audit.loginFailure(request.username(), "User not found", ip, userAgent);
            throw ApiError.unauthorized("Invalid credentials");
        }
        Map<String, Object> user = users.get(0);
        long userId = ((Number) user.get("id")).longValue();
        boolean active = Boolean.TRUE.equals(user.get("is_active"));
        if (!active) {
            audit.loginFailure(request.username(), "Account is inactive", ip, userAgent);
            // Same message as a wrong password: a distinct response would
            // enumerate which identifiers map to real (disabled) accounts.
            throw ApiError.unauthorized("Invalid credentials");
        }
        ensureNotLocked(userId, request.username(), ip, userAgent);

        String storedHash = (String) user.get("password_hash");
        int maxAttempts = maxLoginAttempts();
        boolean valid = storedHash != null
                && bcrypt.matches(request.password(), storedHash);
        if (!valid) {
            // One atomic statement increments and decides the lockout, so
            // concurrent guesses each cost exactly one increment.
            Map<String, Object> row = jdbc.queryForMap("""
                    UPDATE users SET failed_login_attempts = failed_login_attempts + 1,
                        is_locked = (failed_login_attempts + 1 >= ?),
                        locked_until = CASE
                            WHEN failed_login_attempts + 1 >= ? THEN ?
                            ELSE locked_until END
                    WHERE id = ?
                    RETURNING failed_login_attempts, is_locked
                    """, maxAttempts, maxAttempts,
                    Timestamp.from(Instant.now().plus(Duration.ofMinutes(LOCKOUT_MINUTES))),
                    userId);
            int attempts = ((Number) row.get("failed_login_attempts")).intValue();
            boolean locked = Boolean.TRUE.equals(row.get("is_locked"));
            if (locked) {
                revokeAllUserTokens(userId);
                audit.loginFailure(request.username(),
                        "Account locked after max attempts", ip, userAgent);
                throw ApiError.tooManyRequests(
                        "Account locked due to too many failed login attempts. "
                                + "Try again in 30 minutes.");
            }
            audit.loginFailure(request.username(), "Invalid password", ip, userAgent);
            throw ApiError.unauthorized("Invalid credentials. "
                    + (maxAttempts - attempts) + " attempt(s) remaining before account lockout.");
        }

        // Checked only after the password verifies: reporting an unverified
        // account any earlier would confirm the identifier is registered to
        // anyone who probes it.
        if (!properties.isSkipEmailVerification()
                && !Boolean.TRUE.equals(user.get("is_verified"))) {
            throw ApiError.unauthorized(
                    "Please verify your email address before logging in. "
                            + "Check your email for the verification link.");
        }

        boolean twoFactorEnabled = Boolean.TRUE.equals(user.get("two_factor_enabled"));
        Integer recoveryCodesRemaining = null;
        if (twoFactorEnabled) {
            String submittedCode = request.totpCode();
            if (submittedCode == null || submittedCode.isBlank()) {
                audit.loginFailure(request.username(), "2FA code not provided", ip, userAgent);
                throw ApiError.unauthorized(
                        "2FA required. Please provide a TOTP code or recovery code.");
            }
            String stored = (String) user.get("two_factor_secret");
            if (stored == null) {
                throw ApiError.internal("2FA secret missing");
            }
            String secret = totpSecrets.open(stored);
            // TOTP first, then recovery-code fallback — the same order the
            // 2FA-disable flow uses. Recovery codes must work here: this is the
            // only unauthenticated surface, so without it a user who lost their
            // authenticator is locked out despite holding valid codes.
            boolean validTotp = totpVerifier.verify(secret, submittedCode);
            if (!validTotp) {
                List<String> recoveryCodes = recoveryCodeList(user.get("two_factor_recovery_codes"));
                // check_recovery_code identifies the matching stored entry
                // (constant-time, hash or legacy plaintext); the guarded
                // consume then spends that exact entry atomically, so a
                // concurrent login replaying the same code loses the race.
                Integer index = TotpSecrets.checkRecoveryCode(submittedCode, recoveryCodes);
                Integer consumed = index == null ? null
                        : consumeRecoveryCode(userId, recoveryCodes.get(index));
                if (consumed == null) {
                    audit.loginFailure(request.username(), "Invalid 2FA code", ip, userAgent);
                    throw ApiError.unauthorized("Invalid 2FA code");
                }
                recoveryCodesRemaining = consumed;
                if (consumed <= 3) {
                    // A nearly-empty recovery set is worth flagging server-side;
                    // the response field lets the client warn the user too.
                }
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("context", "login");
                details.put("recovery_codes_remaining", consumed);
                auditWriter.event(userId, "two_factor_recovery_code_used", "user", userId,
                        details, ip, userAgent);
            }
        }

        jdbc.update("UPDATE users SET failed_login_attempts = 0, locked_until = NULL,"
                + " is_locked = false WHERE id = ?", userId);

        String loginMethod = recoveryCodesRemaining != null ? "password+2fa_recovery"
                : (twoFactorEnabled ? "password+2fa" : "password");
        LoginResult result = issueAuthenticatedResponse(user, ip, userAgent, clientTimezone);
        result = new LoginResult(new AuthResponse(
                result.response().accessToken(), result.response().user(),
                result.response().roles(), result.response().permissions(),
                result.response().routePolicies(), result.response().isFirstLogin(),
                recoveryCodesRemaining, result.response().profileComplete(),
                result.response().missingProfileFields(),
                result.response().twoFactorEnrollmentRequired(),
                result.response().twoFactorEnrollmentDeadline()), result.refreshToken());
        audit.loginSuccess(userId, loginMethod, ip, userAgent);
        return result;
    }

    // ---- Google sign-in -------------------------------------------------------

    /**
     * {@code login_with_google}: verify the ID token, resolve to an active
     * guest account (creating one when consent is supplied), then mint the
     * shared session. Consent is only required when creating an account — an
     * existing Google session does not re-ask.
     */
    public LoginResult loginWithGoogle(com.hotelapp.auth.dto.AuthDtos.GoogleLoginRequest req,
            String ip, String userAgent, String clientTimezone) {
        if (req.credential() == null || req.credential().isBlank()) {
            throw ApiError.badRequest("Google credential is required");
        }
        var identity = googleIdentity.verifyIdToken(req.credential());
        var context = new com.hotelapp.consent.Consents.Context(ip, userAgent);
        var consentsList = req.consents() == null
                ? List.<com.hotelapp.portal.PortalModels.ConsentAcceptance>of()
                : req.consents();
        String languagePreference = com.hotelapp.consent.Consents.preferredLocale(consentsList);

        GoogleAuthTx.NewAccountConsent newAccount = null;
        if (!consentsList.isEmpty()) {
            com.hotelapp.consent.Consents.validateLocales(consentsList);
            com.hotelapp.consent.Consents.requireConsents(consentsList,
                    com.hotelapp.consent.Consents.REGISTRATION_REQUIRED);
            newAccount = new GoogleAuthTx.NewAccountConsent(consentsList, context,
                    languagePreference);
        }

        GoogleAuthTx.Resolution resolution = googleAuthTx.resolve(identity, newAccount);
        Map<String, Object> user = resolution.user();
        if (resolution.created() && resolution.guestId() != null) {
            guestComms.recordSignupMarketingConsent(resolution.guestId(),
                    req.marketingOptIn(), "registration",
                    com.hotelapp.consent.Consents.Document.PRIVACY_NOTICE.currentVersion(),
                    ip, userAgent);
        }

        long userId = ((Number) user.get("id")).longValue();
        ensureNotLocked(userId, (String) user.get("username"), ip, userAgent);
        jdbc.update("UPDATE users SET failed_login_attempts = 0, locked_until = NULL,"
                + " is_locked = false WHERE id = ?", userId);
        LoginResult result = issueAuthenticatedResponse(user, ip, userAgent, clientTimezone);
        audit.loginSuccess(userId, "google", ip, userAgent);
        return result;
    }

    // ---- registration / verification ----------------------------------------

    /**
     * {@code register}: guest self-registration with consent-by-notice. Consent
     * is checked before any row is written — there is no path that creates an
     * account without provable consent.
     */
    public Map<String, Object> register(RegisterRequest req, String ip, String userAgent) {
        String email = req.email() == null ? null
                : Sanitizer.sanitizeEmail(req.email());
        if (email != null && email.isEmpty()) {
            email = null;
        }
        String phone = Sanitizer.sanitizePhone(req.phone() == null ? "" : req.phone());
        String addressLine1 = req.addressLine1() == null ? null
                : Sanitizer.sanitizeText(req.addressLine1().trim());
        if (addressLine1 != null && addressLine1.isEmpty()) {
            addressLine1 = null;
        }

        // Validators: username pattern + length, email format, password, names,
        // phone digits — same messages as upstream's model validation.
        String username = req.username();
        if (username == null || !username.matches("^[A-Za-z0-9._-]{3,50}$")) {
            throw ApiError.badRequest(
                    "Username may only contain letters, digits, dots, underscores and dashes");
        }
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiError.badRequest("Invalid email format");
        }
        String password = req.password();
        if (password == null || password.length() < 8 || password.length() > 100) {
            throw ApiError.badRequest("Password must be at least 8 characters long");
        }
        String firstName = req.firstName();
        String lastName = req.lastName();
        if (firstName == null || firstName.isBlank() || firstName.length() > 50) {
            throw ApiError.badRequest("First name is required");
        }
        if (lastName == null || lastName.isBlank() || lastName.length() > 50) {
            throw ApiError.badRequest("Last name is required");
        }
        // Upstream validates after sanitize_phone has run: the 8..=16 bound sees
        // digits + an optional leading '+' — separators are already stripped.
        if (phone.length() < 8 || phone.length() > 16) {
            throw ApiError.badRequest(
                    "Phone number must contain between 8 and 15 digits");
        }
        if (addressLine1 != null && addressLine1.length() > 255) {
            throw ApiError.badRequest("Address is too long");
        }

        // Consent is checked before any row is written.
        List<com.hotelapp.portal.PortalModels.ConsentAcceptance> consentsList =
                req.consents() == null
                        ? List.<com.hotelapp.portal.PortalModels.ConsentAcceptance>of()
                        : req.consents();
        com.hotelapp.consent.Consents.validateLocales(consentsList);
        com.hotelapp.consent.Consents.requireConsents(consentsList,
                com.hotelapp.consent.Consents.REGISTRATION_REQUIRED);

        firstName = Sanitizer.sanitizeGuestName(firstName);
        lastName = Sanitizer.sanitizeGuestName(lastName);
        validatePassword(password);

        String emailExistsCheck = email;
        boolean taken = !jdbc.queryForList(
                "SELECT id FROM users WHERE username = ? OR (? IS NOT NULL AND email = ?) LIMIT 1",
                username, emailExistsCheck, emailExistsCheck).isEmpty();
        if (taken) {
            throw ApiError.badRequest("Username or email already exists");
        }

        String fullName = firstName + " " + lastName;
        if (nickNameConflictId(fullName, null) != null) {
            throw ApiError.conflict(
                    "A guest profile with this name already exists. Please sign in with your"
                            + " existing account or contact the hotel for help.");
        }

        String passwordHash = bcrypt.encode(password);
        String languagePreference =
                com.hotelapp.consent.Consents.preferredLocale(consentsList);

        String finalEmail = email;
        String finalPhone = phone;
        String finalAddress = addressLine1;
        String finalFirst = firstName;
        String finalLast = lastName;
        Map<String, Object> created = tx.execute(status -> {
            // register_guest_user: guest row + user row + guest role in one tx.
            Long guestId = jdbc.query(
                    """
                    INSERT INTO guests (first_name, last_name, nick_name, email, phone,
                        address_line_1, is_active, guest_type, language_preference, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, true, 'non_member', ?, CURRENT_TIMESTAMP)
                    RETURNING id
                    """,
                    rs -> rs.next() ? rs.getLong("id") : null,
                    finalFirst, finalLast, fullName, finalEmail, finalPhone,
                    finalAddress, languagePreference);
            if (guestId == null) {
                throw ApiError.conflict(
                        "A guest profile with this name already exists. Please sign in with your"
                                + " existing account or contact the hotel for help.");
            }

            String accountEmail = finalEmail != null
                    ? finalEmail : username + "@no-email.invalid";
            boolean isVerified = finalEmail == null;
            List<Map<String, Object>> userRows = jdbc.queryForList("""
                    INSERT INTO users (uuid, username, email, password_hash, full_name, phone,
                        user_type, guest_id, is_active, is_verified, created_at)
                    VALUES (?::uuid, ?, ?, ?, ?, ?, 'guest', ?, true, ?, CURRENT_TIMESTAMP)
                    RETURNING id, username
                    """, UUID.randomUUID().toString(), username, accountEmail, passwordHash,
                    fullName, finalPhone, guestId, isVerified);
            long newUserId = ((Number) userRows.get(0).get("id")).longValue();

            Long guestRoleId;
            try {
                guestRoleId = jdbc.queryForObject(
                        "SELECT id FROM roles WHERE name = 'guest' LIMIT 1", Long.class);
            } catch (Exception e) {
                throw ApiError.database("Guest role not found: " + e.getMessage());
            }
            jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)"
                    + " ON CONFLICT DO NOTHING", newUserId, guestRoleId);

            consents.record(com.hotelapp.consent.Consents.Subject.user(newUserId)
                            .withGuest(guestId), consentsList,
                    com.hotelapp.consent.Consents.Source.REGISTRATION,
                    new com.hotelapp.consent.Consents.Context(ip, userAgent));
            return Map.of("userId", newUserId, "guestId", guestId);
        });

        long newUserId = ((Number) created.get("userId")).longValue();
        long guestId = ((Number) created.get("guestId")).longValue();

        // Marketing goes to the notification consent ledger, which owns the
        // unsubscribe link. A refusal is recorded there explicitly rather than
        // left absent, so "asked and declined" stays distinguishable from
        // "never asked".
        guestComms.recordSignupMarketingConsent(guestId, req.marketingOptIn(),
                "registration",
                com.hotelapp.consent.Consents.Document.PRIVACY_NOTICE.currentVersion(),
                ip, userAgent);

        // Mints the token AND queues the mail carrying it. Best-effort: the
        // account is already committed, so a mail failure must not fail the
        // registration — the guest can ask for another link.
        if (finalEmail != null) {
            accountEmails.trySendEmailVerification(newUserId);
        }

        String message = finalEmail != null
                ? "Registration successful! Please check your email to verify your account."
                : "Registration successful! You can now log in with your username.";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("user", Map.of(
                "id", newUserId,
                "username", username,
                "email", finalEmail == null ? "" : finalEmail,
                "full_name", fullName,
                "user_type", "guest",
                "is_verified", finalEmail == null));
        body.put("guest_id", guestId);
        return body;
    }

    /** {@code verify_email}: redeem a verification token. */
    public Map<String, Object> verifyEmail(String token) {
        if (token == null || token.length() < 32 || token.length() > 256) {
            throw ApiError.badRequest("Invalid verification token");
        }
        Long userId = jdbc.query("""
                UPDATE users
                SET is_verified = true, email_verification_token = NULL,
                    email_token_expires_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE email_verification_token = ?
                  AND email_token_expires_at > CURRENT_TIMESTAMP
                  AND is_verified = false
                RETURNING id
                """, rs -> rs.next() ? rs.getLong("id") : null, sha256Hex(token));
        if (userId == null) {
            throw ApiError.badRequest("Invalid or expired verification token");
        }
        return Map.of("message", "Email verified successfully", "user_id", userId);
    }

    /**
     * {@code resend_verification}: deliberately generic — never reveals whether
     * an address is registered, so it always returns the same message.
     */
    public Map<String, Object> resendVerification(String email) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiError.badRequest("Invalid email format");
        }
        String sanitized = Sanitizer.sanitizeEmail(email);
        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT id, is_verified FROM users
                WHERE email = ? AND deleted_at IS NULL
                """, sanitized);
        if (!users.isEmpty()
                && !Boolean.TRUE.equals(users.get(0).get("is_verified"))) {
            long userId = ((Number) users.get(0).get("id")).longValue();
            accountEmails.trySendEmailVerification(userId);
        }
        return Map.of("message",
                "If that account needs verification, a new email has been sent.");
    }

    // ---- session minting ----------------------------------------------------

    public RefreshResult refresh(String rawToken) {
        requireToken(rawToken);
        String tokenHash = sha256Hex(rawToken);
        Map<String, Object> session = findActiveSession(tokenHash);
        long userId = ((Number) session.get("user_id")).longValue();
        String sessionId = String.valueOf(session.get("id"));

        Map<String, Object> user = findUser(userId);
        boolean active = Boolean.TRUE.equals(user.get("is_active"));
        if (!active) {
            revokeAllUserTokens(userId);
            throw ApiError.unauthorized("Account is inactive");
        }

        // Upstream `refresh` reads the lock state directly: a still-locked
        // account revokes the presented token, and a lock without an expiry is
        // permanent until staff intervene — not "elapsed".
        Map<String, Object> lockState = jdbc.queryForMap(
                "SELECT is_locked, locked_until FROM users WHERE id = ?", userId);
        if (Boolean.TRUE.equals(lockState.get("is_locked"))) {
            Instant until = toInstant(lockState.get("locked_until"));
            if (until == null) {
                revokeRefreshToken(rawToken);
                throw ApiError.unauthorized("Account is locked");
            }
            if (Instant.now().isBefore(until)) {
                long remainingMins = Duration.between(Instant.now(), until).toMinutes() + 1;
                revokeRefreshToken(rawToken);
                throw ApiError.tooManyRequests(
                        "Account is locked due to too many failed attempts."
                                + " Try again in " + remainingMins + " minute(s).");
            }
            jdbc.update("UPDATE users SET is_locked = false, locked_until = NULL,"
                    + " failed_login_attempts = 0 WHERE id = ?", userId);
        }

        List<String> roles = roleNames(userId);
        String newToken = generateRawToken();
        String newHash = sha256Hex(newToken);
        int rotated = jdbc.update("""
                UPDATE refresh_tokens SET token_hash = ?, expires_at = NOW() + INTERVAL '30 days',
                    last_used_at = CURRENT_TIMESTAMP
                WHERE id = CAST(? AS uuid) AND token_hash = ?
                  AND revoked_at IS NULL AND is_revoked = false
                """, newHash, sessionId, tokenHash);
        if (rotated == 0) {
            throw ApiError.unauthorized("Refresh token was already used or revoked");
        }
        String accessToken = jwtService.issueAccessToken(userId, (String) user.get("username"),
                roles, sessionId);
        return new RefreshResult(new RefreshTokenResponse(accessToken), newToken);
    }

    /** {@code revoke_refresh_token}: mark one session row revoked by hash. */
    public void revokeRefreshToken(String rawToken) {
        jdbc.update("""
                UPDATE refresh_tokens SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = ?
                """, sha256Hex(rawToken));
    }

    public void logout(String rawToken) {
        requireToken(rawToken);
        jdbc.update("""
                UPDATE refresh_tokens SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP
                WHERE token_hash = ?
                """, sha256Hex(rawToken));
    }

    public AccessSnapshot accessSnapshot(long userId) {
        Map<String, Object> user = findUser(userId);
        boolean active = Boolean.TRUE.equals(user.get("is_active"));
        if (!active) {
            revokeAllUserTokens(userId);
            throw ApiError.unauthorized("Account is inactive");
        }
        return new AccessSnapshot(roleNames(userId), permissionNames(userId),
                routePolicies());
    }

    /**
     * {@code issue_authenticated_response}: the only place a session is minted.
     * Enforced here so every door — password, Google, passkey — is covered by
     * construction. Checked before the refresh token and JWT exist, so a
     * refused sign-in leaves nothing behind to revoke.
     */
    public LoginResult issueAuthenticatedResponse(Map<String, Object> user, String ip,
            String userAgent, String clientTimezone) {
        long userId = ((Number) user.get("id")).longValue();
        List<String> roles = roleNames(userId);

        Instant twoFactorDeadline = null;
        switch (twoFactorPolicy(user, roles)) {
            case TwoFactorPolicy.Satisfied ignored -> {
            }
            case TwoFactorPolicy.Grace(Instant deadline) -> twoFactorDeadline = deadline;
            case TwoFactorPolicy.Expired ignored -> {
                auditWriter.event(userId, "two_factor_enrollment_blocked", "user", userId,
                        null, ip, userAgent);
                throw ApiError.twoFactorEnrollmentRequired();
            }
        }

        List<String> permissions = permissionNames(userId);
        String rawToken = generateRawToken();
        boolean firstLogin = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT last_login_at IS NULL FROM users WHERE id = ?", Boolean.class, userId));
        UUID sessionId = insertSession(userId, sha256Hex(rawToken), ip, userAgent,
                clientTimezone);
        String accessToken = jwtService.issueAccessToken(userId,
                (String) user.get("username"), roles, sessionId.toString());
        jdbc.update("UPDATE users SET last_login_at = CURRENT_TIMESTAMP, "
                + "last_login_ip = COALESCE(CAST(? AS text)::inet, last_login_ip) WHERE id = ?",
                ip, userId);

        var completion = profileCompletionForUser(userId);
        UserResponse userResponse = new UserResponse(userId,
                (String) user.get("username"), (String) user.get("email"),
                (String) user.get("full_name"), (String) user.get("phone"),
                Boolean.TRUE.equals(user.get("is_active")), roles, permissions,
                toInstant(user.get("created_at")),
                toInstant(user.get("updated_at")));
        AuthResponse response = new AuthResponse(accessToken, userResponse, roles, permissions,
                routePolicies(), firstLogin, null, completion.complete(),
                completion.missingFields(), twoFactorDeadline != null, twoFactorDeadline);
        return new LoginResult(response, rawToken);
    }

    // ---- 2FA enrolment policy ------------------------------------------------

    /** Whether a sign-in satisfies the role-based two-factor enrolment policy. */
    public sealed interface TwoFactorPolicy {
        record Satisfied() implements TwoFactorPolicy {
        }
        record Grace(Instant deadline) implements TwoFactorPolicy {
        }
        record Expired() implements TwoFactorPolicy {
        }
    }

    /**
     * {@code two_factor_policy}: the grace window runs from the later of the
     * account's creation and the moment an administrator last wrote
     * {@code require_two_factor_roles}, so existing staff get a full window
     * when the policy is switched on and a new hire gets one from their own
     * start date. Editing the setting therefore restarts every unenrolled
     * user's window — widening the policy must not lock out a shift that never
     * had a chance to enrol.
     */
    public TwoFactorPolicy twoFactorPolicy(Map<String, Object> user, List<String> roles) {
        long userId = ((Number) user.get("id")).longValue();
        String configured = settings.getString(REQUIRE_TWO_FACTOR_ROLES, "");
        List<String> required = java.util.Arrays.stream(configured.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        // Policy off: the common path costs nothing beyond one cached read.
        if (required.isEmpty()) {
            return new TwoFactorPolicy.Satisfied();
        }

        boolean inScope = roles.stream().anyMatch(role -> required.stream()
                        .anyMatch(want -> want.equalsIgnoreCase(role)))
                || isSuperAdmin(userId);
        if (!inScope) {
            return new TwoFactorPolicy.Satisfied();
        }

        // A passkey is a second factor in its own right — passkey already
        // treats one as satisfying 2FA — so demanding TOTP on top would push
        // users off a phishing-resistant credential onto a weaker one.
        if (Boolean.TRUE.equals(user.get("two_factor_enabled"))
                || passkeyCount(userId) > 0) {
            return new TwoFactorPolicy.Satisfied();
        }

        long graceDays = Math.max(0, settings.getInt(REQUIRE_TWO_FACTOR_GRACE_DAYS, 14));
        Instant policySince = settings.updatedAt(REQUIRE_TWO_FACTOR_ROLES);
        if (policySince == null) {
            policySince = Instant.now();
        }
        Instant userCreated = toInstant(user.get("created_at"));
        Instant deadline = (userCreated != null && userCreated.isAfter(policySince)
                ? userCreated : policySince).plus(Duration.ofDays(graceDays));

        if (Instant.now().isBefore(deadline)) {
            return new TwoFactorPolicy.Grace(deadline);
        }
        return new TwoFactorPolicy.Expired();
    }

    /** {@code passkey_count}: active credentials — a passkey satisfies 2FA. */
    public long passkeyCount(long userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM passkeys WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    /** {@code is_super_admin}: users.is_super_admin on an active, undeleted row. */
    public boolean isSuperAdmin(long userId) {
        Boolean flag = jdbc.queryForObject(
                "SELECT is_super_admin FROM users WHERE id = ? AND is_active = true"
                        + " AND deleted_at IS NULL",
                Boolean.class, userId);
        return Boolean.TRUE.equals(flag);
    }

    // ---- step-up + lockout -------------------------------------------------

    /**
     * {@code ensure_step_up}: password OR TOTP — whichever the account actually
     * has configured. A passkey satisfies 2FA on its own, so registering one
     * must cost more than an already-open session.
     */
    public void ensureStepUp(long userId, String password, String totpCode) {
        String storedHash = passwordHash(userId);

        if (password != null && storedHash != null
                && bcrypt.matches(password, storedHash)) {
            return;
        }

        var twoFactor = twoFactorState(userId);
        boolean twoFactorEnabled = Boolean.TRUE.equals(twoFactor.enabled());
        if (twoFactorEnabled && totpCode != null && twoFactor.secret() != null) {
            String secret;
            try {
                secret = totpSecrets.open(twoFactor.secret());
            } catch (IllegalStateException e) {
                secret = null;
            }
            if (secret != null && totpVerifier.verify(secret, totpCode)) {
                return;
            }
        }

        if (storedHash == null && !twoFactorEnabled) {
            throw ApiError.badRequest(
                    "Set a password or enable two-factor authentication before registering"
                            + " a passkey.");
        }
        throw ApiError.unauthorized(
                "Re-enter your password (or a two-factor code) to register a passkey.");
    }

    /**
     * {@code ensure_not_locked}: reject a login attempt against a locked
     * account, clearing the lock when it has already elapsed. Shared by the
     * password and passkey doors — the two paths must apply the same rule or
     * the lockout is only as strong as its weakest door.
     */
    public void ensureNotLocked(long userId, String username, String ip, String userAgent) {
        Map<String, Object> state = jdbc.queryForMap(
                "SELECT is_locked, locked_until FROM users WHERE id = ?", userId);
        if (!Boolean.TRUE.equals(state.get("is_locked"))) {
            return;
        }
        Instant until = toInstant(state.get("locked_until"));
        if (until != null && until.isAfter(Instant.now())) {
            long remainingMins = Duration.between(Instant.now(), until).toMinutes() + 1;
            audit.loginFailure(username, "Account locked", ip, userAgent);
            throw ApiError.tooManyRequests("Account is locked due to too many failed attempts."
                    + " Try again in " + remainingMins + " minute(s).");
        }
        // Lock elapsed — unlock_user clears the flag AND the attempt counter.
        jdbc.update("UPDATE users SET is_locked = false, locked_until = NULL,"
                + " failed_login_attempts = 0 WHERE id = ?", userId);
    }

    // ---- 2FA / passkey repository helpers -------------------------------------

    String passwordHash(long userId) {
        try {
            return jdbc.queryForObject(
                    "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        } catch (Exception e) {
            return null;
        }
    }

    public record TwoFactorState(Boolean enabled, String secret, List<String> recoveryCodes) {
    }

    /** {@code AuthRepository::two_factor_state}: the 2FA columns for a user. */
    public TwoFactorState twoFactorState(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT two_factor_enabled, two_factor_secret, two_factor_recovery_codes"
                        + " FROM users WHERE id = ?", userId);
        if (rows.isEmpty()) {
            return new TwoFactorState(null, null, List.of());
        }
        Map<String, Object> row = rows.get(0);
        return new TwoFactorState((Boolean) row.get("two_factor_enabled"),
                (String) row.get("two_factor_secret"),
                recoveryCodeList(row.get("two_factor_recovery_codes")));
    }

    /**
     * {@code create_2fa_challenge}: mint a single-use challenge for a purpose;
     * only its SHA-256 hash is stored, like refresh tokens.
     */
    public String create2faChallenge(long userId, String purpose) {
        String challengeCode = generateRawToken();
        String challengeHash = sha256Hex(challengeCode);
        jdbc.update("""
                INSERT INTO two_factor_challenges (user_id, challenge_code, purpose, expires_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, purpose) DO UPDATE SET
                    challenge_code = EXCLUDED.challenge_code,
                    expires_at = EXCLUDED.expires_at,
                    created_at = CURRENT_TIMESTAMP
                """, userId, challengeHash, purpose,
                Timestamp.from(Instant.now().plus(Duration.ofMinutes(10))));
        return challengeCode;
    }

    /**
     * {@code consume_2fa_challenge}: atomically spend a matching, unexpired
     * challenge — a mismatch or an expired row leaves the table untouched.
     */
    public boolean consume2faChallenge(long userId, String purpose, String challengeCode) {
        int deleted = jdbc.update("""
                DELETE FROM two_factor_challenges
                WHERE user_id = ? AND purpose = ? AND challenge_code = ?
                  AND expires_at > CURRENT_TIMESTAMP
                """, userId, purpose, sha256Hex(challengeCode));
        return deleted == 1;
    }

    /** {@code update_recovery_codes}: replace the stored code set wholesale. */
    public void updateRecoveryCodes(long userId, List<String> recoveryCodes) {
        jdbc.update("UPDATE users SET two_factor_recovery_codes = ?,"
                        + " updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                totpSecrets.recoveryCodesForStorage(recoveryCodes).toArray(new String[0]),
                userId);
    }

    /**
     * {@code consume_recovery_code}: guarded array_remove spends the stored
     * entry only if still present — concurrent logins can't both accept the
     * same code, and a concurrent regeneration is never clobbered.
     */
    public Integer consumeRecoveryCode(long userId, String storedEntry) {
        List<Integer> remaining = jdbc.queryForList("""
                UPDATE users
                SET two_factor_recovery_codes = array_remove(two_factor_recovery_codes, ?),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND ? = ANY(two_factor_recovery_codes)
                RETURNING COALESCE(array_length(two_factor_recovery_codes, 1), 0)
                """, Integer.class, storedEntry, userId, storedEntry);
        return remaining.isEmpty() ? null : remaining.get(0);
    }

    /** {@code revoke_all_user_tokens}: every refresh token for the user. */
    public void revokeAllUserTokens(long userId) {
        jdbc.update("""
                UPDATE refresh_tokens SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND revoked_at IS NULL AND is_revoked = false
                """, userId);
    }

    /**
     * {@code list_active_sessions}: active refresh-token sessions for the
     * profile sessions surface, newest-used first.
     */
    public List<Map<String, Object>> listActiveSessions(long userId) {
        return jdbc.queryForList("""
                SELECT id::text AS id, user_agent, host(ip_address) AS ip_address,
                       created_at, last_used_at, expires_at, client_timezone
                FROM refresh_tokens
                WHERE user_id = ? AND expires_at > CURRENT_TIMESTAMP
                  AND revoked_at IS NULL AND is_revoked = false
                ORDER BY last_used_at DESC NULLS LAST, created_at DESC
                """, userId);
    }

    /** {@code revoke_user_session}: revoke one session owned by the caller. */
    public boolean revokeUserSession(long userId, String sessionId) {
        int updated = jdbc.update("""
                UPDATE refresh_tokens
                SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP, revoked_by = ?
                WHERE id = ?::uuid AND user_id = ?
                  AND revoked_at IS NULL AND is_revoked = false
                """, userId, sessionId, userId);
        return updated == 1;
    }

    // ---- profile completion -------------------------------------------------

    /** {@code completion_for_user}: guest accounts only; non-guests are exempt. */
    public com.hotelapp.auth.GoogleIdentityService.ProfileCompletion profileCompletionForUser(
            long userId) {
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
            return new com.hotelapp.auth.GoogleIdentityService.ProfileCompletion(true, List.of());
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT first_name, last_name, phone FROM guests WHERE id = ?"
                        + " AND deleted_at IS NULL",
                guestId);
        if (rows.isEmpty()) {
            return com.hotelapp.auth.GoogleIdentityService
                    .profileCompletion(null, null, null);
        }
        Map<String, Object> guest = rows.get(0);
        return com.hotelapp.auth.GoogleIdentityService.profileCompletion(
                (String) guest.get("first_name"), (String) guest.get("last_name"),
                (String) guest.get("phone"));
    }

    // ---- misc helpers ---------------------------------------------------------

    /** {@code validate_password}: upstream complexity rules verbatim. */
    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw ApiError.badRequest("Password must be at least 8 characters long");
        }
        if (password.length() > 128) {
            throw ApiError.badRequest("Password must not exceed 128 characters");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw ApiError.badRequest("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw ApiError.badRequest("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw ApiError.badRequest("Password must contain at least one number");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw ApiError.badRequest(
                    "Password must contain at least one special character");
        }
        String lower = password.toLowerCase();
        for (String weak : WEAK_PASSWORDS) {
            if (lower.contains(weak)) {
                throw ApiError.badRequest("Password is too common or weak");
            }
        }
    }

    private static final List<String> WEAK_PASSWORDS = List.of(
            "password", "password123", "12345678", "qwerty123", "abc123456",
            "password1", "welcome123", "admin123", "letmein123", "monkey123");

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

    @SuppressWarnings("unchecked")
    static List<String> recoveryCodeList(Object raw) {
        if (raw instanceof java.sql.Array array) {
            try {
                Object elements = array.getArray();
                if (elements instanceof Object[] arr) {
                    List<String> out = new ArrayList<>(arr.length);
                    for (Object element : arr) {
                        out.add(String.valueOf(element));
                    }
                    return out;
                }
            } catch (Exception e) {
                return List.of();
            }
        }
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (raw instanceof String[] arr) {
            return List.of(arr);
        }
        return List.of();
    }

    private int maxLoginAttempts() {
        try {
            String value = jdbc.queryForObject(
                    "SELECT value FROM system_settings WHERE key = 'max_login_attempts'",
                    String.class);
            return value == null ? 5 : Math.max(1, Integer.parseInt(value));
        } catch (Exception e) {
            return 5;
        }
    }

    private Map<String, Object> findActiveSession(String tokenHash) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id::text AS id, user_id FROM refresh_tokens
                WHERE token_hash = ? AND expires_at > CURRENT_TIMESTAMP
                  AND revoked_at IS NULL AND is_revoked = false
                """, tokenHash);
        if (rows.isEmpty()) {
            throw ApiError.unauthorized("Invalid or expired refresh token");
        }
        return rows.get(0);
    }

    private Map<String, Object> findUser(long userId) {
        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT id, username, is_active FROM users WHERE id = ? AND deleted_at IS NULL
                """, userId);
        if (users.isEmpty()) {
            throw ApiError.unauthorized("User not found");
        }
        return users.get(0);
    }

    private List<String> roleNames(long userId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.name FROM roles r
                JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                """, String.class, userId);
    }

    private List<String> permissionNames(long userId) {
        return jdbc.queryForList("""
                WITH effective_roles AS (
                    SELECT ur.role_id FROM user_roles ur
                    WHERE ur.user_id = ? AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                    UNION
                    SELECT tr.role_id FROM team_roles tr
                    JOIN team_members tm ON tm.team_id = tr.team_id
                    JOIN teams t ON t.id = tm.team_id
                    WHERE tm.user_id = ? AND t.is_active AND t.deleted_at IS NULL
                      AND (tm.expires_at IS NULL OR tm.expires_at > CURRENT_TIMESTAMP)
                )
                SELECT DISTINCT p.name FROM permissions p
                JOIN role_permissions rp ON p.id = rp.permission_id
                JOIN effective_roles er ON er.role_id = rp.role_id
                """, String.class, userId, userId);
    }

    private List<RouteAccessPolicyDto> routePolicies() {
        return jdbc.query("""
                SELECT route_id, path, nav_label, nav_group, required_permissions,
                       required_roles, excluded_roles, nav_permissions, nav_roles,
                       nav_excluded_roles, is_navigation
                FROM route_access_policies ORDER BY route_id
                """, (rs, n) -> new RouteAccessPolicyDto(
                rs.getString("route_id"), rs.getString("path"),
                rs.getString("nav_label"), rs.getString("nav_group"),
                jsonList(rs.getString("required_permissions")),
                jsonList(rs.getString("required_roles")),
                jsonList(rs.getString("excluded_roles")),
                jsonList(rs.getString("nav_permissions")),
                jsonList(rs.getString("nav_roles")),
                jsonList(rs.getString("nav_excluded_roles")),
                rs.getBoolean("is_navigation")));
    }

    private List<String> jsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, ArrayList.class);
            return raw.stream().map(String::valueOf).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private UUID insertSession(long userId, String tokenHash, String ip, String userAgent,
            String clientTimezone) {
        UUID sessionId = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refresh_tokens
                    (id, user_id, token_hash, ip_address, user_agent, expires_at, client_timezone)
                VALUES (CAST(? AS uuid), ?, ?, CAST(? AS inet), ?, NOW() + INTERVAL '30 days', ?)
                """, sessionId.toString(), userId, tokenHash, ip,
                truncate(userAgent, 512), clientTimezone);
        return sessionId;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw ApiError.badRequest(message);
        }
    }

    private static void requireToken(String token) {
        if (token == null || token.length() < 32 || token.length() > 512) {
            throw ApiError.badRequest("Invalid refresh token");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.chars().limit(max).collect(StringBuilder::new,
                StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    private static java.time.Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.toInstant();
        }
        throw new IllegalStateException("Unexpected temporal value: " + value.getClass());
    }
}
