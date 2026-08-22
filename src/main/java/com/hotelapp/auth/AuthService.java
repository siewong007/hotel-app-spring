package com.hotelapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelapp.auth.dto.AccessSnapshot;
import com.hotelapp.auth.dto.AuthResponse;
import com.hotelapp.auth.dto.LoginRequest;
import com.hotelapp.auth.dto.RefreshTokenResponse;
import com.hotelapp.auth.dto.RouteAccessPolicyDto;
import com.hotelapp.auth.dto.UserResponse;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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

@Service
public class AuthService {

    private static final long REFRESH_TTL_DAYS = 30;
    private static final long LOCKOUT_MINUTES = 30;

    private final JdbcTemplate jdbc;
    private final JwtService jwtService;
    private final AuthAudit audit;
    private final TotpVerifier totpVerifier;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc, JwtService jwtService, AuthAudit audit,
            TotpVerifier totpVerifier, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.jwtService = jwtService;
        this.audit = audit;
        this.totpVerifier = totpVerifier;
        this.objectMapper = objectMapper;
    }

    public record LoginResult(AuthResponse response, String refreshToken) {
    }

    public record RefreshResult(RefreshTokenResponse response, String newRefreshToken) {
    }

    public LoginResult login(LoginRequest request, String ip, String userAgent) {
        requireText(request.username(), "Username is required");
        requireText(request.password(), "Password is required");

        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT id, username, email, password_hash, full_name, phone, is_active,
                       is_verified, two_factor_enabled, two_factor_secret,
                       failed_login_attempts, locked_until, created_at, updated_at
                FROM users WHERE deleted_at IS NULL AND (username = ? OR email = ?)
                """, request.username().toLowerCase(), request.username().toLowerCase());

        if (users.isEmpty()) {
            audit.loginFailure(request.username(), "User not found", ip, userAgent);
            throw ApiError.unauthorized("Invalid credentials");
        }
        Map<String, Object> user = users.get(0);
        long userId = ((Number) user.get("id")).longValue();
        boolean active = Boolean.TRUE.equals(user.get("is_active"));
        if (!active) {
            audit.loginFailure(request.username(), "Account is inactive", ip, userAgent);
            throw ApiError.unauthorized("Account is inactive");
        }
        ensureNotLocked(userId, request.username(), ip, userAgent);

        boolean skipVerification = Boolean.parseBoolean(
                System.getenv().getOrDefault("SKIP_EMAIL_VERIFICATION", "false"));
        if (!skipVerification && !Boolean.TRUE.equals(user.get("is_verified"))) {
            throw ApiError.unauthorized(
                    "Please verify your email address before logging in. "
                            + "Check your email for the verification link.");
        }

        int maxAttempts = maxLoginAttempts();
        String storedHash = (String) user.get("password_hash");
        boolean valid = storedHash != null && new BCryptPasswordEncoder().matches(request.password(), storedHash);
        if (!valid) {
            Map<String, Object> row = jdbc.queryForMap("""
                    UPDATE users SET failed_login_attempts = failed_login_attempts + 1,
                        locked_until = CASE
                            WHEN failed_login_attempts + 1 >= ? THEN NOW() + INTERVAL '30 minutes'
                            ELSE locked_until END
                    WHERE id = ?
                    RETURNING failed_login_attempts, (locked_until > NOW()) AS is_locked
                    """, maxAttempts, userId);
            int attempts = ((Number) row.get("failed_login_attempts")).intValue();
            boolean locked = Boolean.TRUE.equals(row.get("is_locked"));
            if (locked) {
                revokeAllTokens(userId);
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

        boolean twoFactorEnabled = Boolean.TRUE.equals(user.get("two_factor_enabled"));
        Integer recoveryCodesRemaining = null;
        if (twoFactorEnabled) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                audit.loginFailure(request.username(), "2FA code not provided", ip, userAgent);
                throw ApiError.unauthorized(
                        "2FA required. Please provide a TOTP code or recovery code.");
            }
            String secret = (String) user.get("two_factor_secret");
            boolean codeValid = secret != null && totpVerifier.verify(secret, request.totpCode());
            if (!codeValid) {
                audit.loginFailure(request.username(), "Invalid 2FA code", ip, userAgent);
                throw ApiError.unauthorized("Invalid 2FA code");
            }
        }

        jdbc.update("UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = ?",
                userId);

        String loginMethod = recoveryCodesRemaining != null ? "password+2fa_recovery"
                : (twoFactorEnabled ? "password+2fa" : "password");
        LoginResult result = issueAuthenticatedResponse(user, ip, userAgent);
        audit.loginSuccess(userId, loginMethod, ip, userAgent);
        return result;
    }

    public RefreshResult refresh(String rawToken) {
        requireToken(rawToken);
        String tokenHash = sha256Hex(rawToken);
        Map<String, Object> session = findActiveSession(tokenHash);
        long userId = ((Number) session.get("user_id")).longValue();
        String sessionId = String.valueOf(session.get("id"));

        Map<String, Object> user = findUser(userId);
        boolean active = Boolean.TRUE.equals(user.get("is_active"));
        if (!active) {
            revokeAllTokens(userId);
            throw ApiError.unauthorized("Account is inactive");
        }
        ensureNotLocked(userId, (String) user.get("username"), null, null);

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
            revokeAllTokens(userId);
            throw ApiError.unauthorized("Account is inactive");
        }
        return new AccessSnapshot(roleNames(userId), permissionNames(userId),
                routePolicies());
    }

    private LoginResult issueAuthenticatedResponse(Map<String, Object> user, String ip,
            String userAgent) {
        long userId = ((Number) user.get("id")).longValue();
        List<String> roles = roleNames(userId);
        List<String> permissions = permissionNames(userId);
        String rawToken = generateRawToken();
        UUID sessionId = insertSession(userId, sha256Hex(rawToken), ip, userAgent);
        String accessToken = jwtService.issueAccessToken(userId,
                (String) user.get("username"), roles, sessionId.toString());
        boolean firstLogin = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT last_login_at IS NULL FROM users WHERE id = ?", Boolean.class, userId));
        jdbc.update("UPDATE users SET last_login_at = CURRENT_TIMESTAMP, "
                + "last_login_ip = COALESCE(CAST(? AS text)::inet, last_login_ip) WHERE id = ?",
                ip, userId);
        UserResponse userResponse = new UserResponse(userId,
                (String) user.get("username"), (String) user.get("email"),
                (String) user.get("full_name"), (String) user.get("phone"),
                Boolean.TRUE.equals(user.get("is_active")), roles, permissions,
                toInstant(user.get("created_at")),
                toInstant(user.get("updated_at")));
        AuthResponse response = new AuthResponse(accessToken, userResponse, roles, permissions,
                routePolicies(), firstLogin, null, true, List.of());
        return new LoginResult(response, rawToken);
    }

    private void ensureNotLocked(long userId, String username, String ip, String userAgent) {
        Map<String, Object> state = jdbc.queryForMap(
                "SELECT is_locked, locked_until FROM users WHERE id = ?", userId);
        if (!Boolean.TRUE.equals(state.get("is_locked"))) {
            return;
        }
        java.time.Instant until = toInstant(state.get("locked_until"));
        if (until != null && until.isAfter(java.time.Instant.now())) {
            long remainingMins = java.time.Duration.between(java.time.Instant.now(), until)
                    .toMinutes() + 1;
            audit.loginFailure(username, "Account locked", ip, userAgent);
            throw ApiError.tooManyRequests("Account is locked due to too many failed attempts."
                    + " Try again in " + remainingMins + " minute(s).");
        }
        jdbc.update("UPDATE users SET is_locked = false, locked_until = NULL WHERE id = ?",
                userId);
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

    private UUID insertSession(long userId, String tokenHash, String ip, String userAgent) {
        UUID sessionId = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refresh_tokens (id, user_id, token_hash, ip_address, user_agent, expires_at)
                VALUES (CAST(? AS uuid), ?, ?, CAST(? AS inet), ?, NOW() + INTERVAL '30 days')
                """, sessionId.toString(), userId, tokenHash, ip,
                truncate(userAgent, 512));
        return sessionId;
    }

    private void revokeAllTokens(long userId) {
        jdbc.update("""
                UPDATE refresh_tokens SET is_revoked = true, revoked_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND revoked_at IS NULL AND is_revoked = false
                """, userId);
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
