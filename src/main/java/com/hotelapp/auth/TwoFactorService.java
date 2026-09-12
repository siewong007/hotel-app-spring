package com.hotelapp.auth;

import com.hotelapp.auth.dto.AuthDtos.RegenerateBackupCodesRequest;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorDisableRequest;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorEnableRequest;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorStatusResponse;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorVerifyRequest;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.settings.HotelSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Port of {@code services/two_factor.rs}: the six 2FA workflows — setup, enable,
 * disable, status, verify, regenerate backup codes.
 */
@Service
public class TwoFactorService {

    private final JdbcTemplate jdbc;
    private final AuthService authService;
    private final TotpSecrets totpSecrets;
    private final TotpVerifier totpVerifier;
    private final HotelSettings settings;
    private final AuditWriter audit;

    public TwoFactorService(JdbcTemplate jdbc, AuthService authService,
            TotpSecrets totpSecrets, TotpVerifier totpVerifier, HotelSettings settings,
            AuditWriter audit) {
        this.jdbc = jdbc;
        this.authService = authService;
        this.totpSecrets = totpSecrets;
        this.totpVerifier = totpVerifier;
        this.settings = settings;
        this.audit = audit;
    }

    private Map<String, Object> getUser(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, username, two_factor_enabled, two_factor_secret,
                       two_factor_recovery_codes
                FROM users WHERE id = ? AND deleted_at IS NULL
                """, userId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("User not found");
        }
        return rows.get(0);
    }

    /**
     * {@code setup_2fa}: mint a fresh TOTP secret (sealed at rest), persist it,
     * then create the setup challenge the enable call must consume.
     */
    public Map<String, Object> setup(long userId) {
        Map<String, Object> user = getUser(userId);
        if (Boolean.TRUE.equals(user.get("two_factor_enabled"))) {
            throw ApiError.badRequest("2FA is already enabled for this account");
        }
        String username = (String) user.get("username");
        String issuerName = settings.getHotelDisplayName("totp_issuer_name");
        TotpSecrets.GeneratedSecret generated = totpSecrets.generateSecret(username, issuerName);
        jdbc.update("UPDATE users SET two_factor_secret = ?, updated_at = CURRENT_TIMESTAMP"
                + " WHERE id = ?", totpSecrets.seal(generated.secretBase32()), userId);

        String challengeCode = authService.create2faChallenge(userId, "setup");
        audit.event(userId, "two_factor_setup_initiated", "user", userId, null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("secret", generated.secretBase32());
        body.put("qr_code_url", generated.qrCodeUrl());
        body.put("challenge_code", challengeCode);
        return body;
    }

    /**
     * {@code enable_2fa}: verify the TOTP code, consume the single-use setup
     * challenge, then persist the enabled flag and fresh backup codes.
     */
    public Map<String, Object> enable(long userId, TwoFactorEnableRequest req) {
        if (req.code() == null || req.code().length() < 6 || req.code().length() > 12) {
            throw ApiError.badRequest("Invalid 2FA code");
        }
        if (req.challengeCode() == null || req.challengeCode().length() != 64) {
            throw ApiError.badRequest("Invalid challenge code");
        }
        Map<String, Object> user = getUser(userId);
        String stored = (String) user.get("two_factor_secret");
        if (stored == null) {
            throw ApiError.badRequest("2FA setup not initiated. Call /auth/2fa/setup first.");
        }
        String twoFactorSecret;
        try {
            twoFactorSecret = totpSecrets.open(stored);
        } catch (IllegalStateException e) {
            throw ApiError.internal(e.getMessage());
        }

        boolean valid = totpVerifier.verify(twoFactorSecret, req.code());
        if (!valid) {
            throw ApiError.badRequest("Invalid 2FA code");
        }

        // Consume the setup challenge only after the code checked out, so a typo
        // leaves the single-use challenge intact for a retry.
        boolean challengeOk =
                authService.consume2faChallenge(userId, "setup", req.challengeCode());
        if (!challengeOk) {
            audit.event(userId, "two_factor_enable_challenge_rejected", "user", userId, null);
            throw ApiError.badRequest(
                    "2FA setup challenge is invalid or has expired. Restart 2FA setup.");
        }

        List<String> backupCodes = totpSecrets.generateBackupCodes();
        jdbc.update("""
                UPDATE users SET two_factor_enabled = true, two_factor_secret = ?,
                    two_factor_recovery_codes = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, twoFactorSecret, totpSecrets.recoveryCodesForStorage(backupCodes)
                        .toArray(new String[0]), userId);

        audit.event(userId, "two_factor_enabled", "user", userId, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "2FA enabled successfully");
        body.put("backup_codes", backupCodes);
        return body;
    }

    /**
     * {@code disable_2fa}: a valid TOTP or an unconsumed recovery code clears
     * the factor and revokes every session.
     */
    public Map<String, Object> disable(long userId, TwoFactorDisableRequest req) {
        if (req.code() == null || req.code().length() < 6 || req.code().length() > 32) {
            throw ApiError.badRequest("Invalid 2FA code");
        }
        Map<String, Object> user = getUser(userId);
        if (!Boolean.TRUE.equals(user.get("two_factor_enabled"))) {
            throw ApiError.badRequest("2FA is not enabled for this account");
        }
        String stored = (String) user.get("two_factor_secret");
        if (stored == null) {
            throw ApiError.internal("2FA secret missing");
        }
        String totpSecret;
        try {
            totpSecret = totpSecrets.open(stored);
        } catch (IllegalStateException e) {
            throw ApiError.internal(e.getMessage());
        }
        List<String> recoveryCodes = recoveryCodes(user);

        boolean codeValid = totpVerifier.verify(totpSecret, req.code());
        if (!codeValid) {
            Integer index = TotpSecrets.checkRecoveryCode(req.code(), recoveryCodes);
            if (index != null) {
                codeValid = true;
                List<String> updated = new java.util.ArrayList<>(recoveryCodes);
                updated.remove((int) index);
                authService.updateRecoveryCodes(userId, updated);
            }
        }
        if (!codeValid) {
            throw ApiError.badRequest(
                    "Invalid code. Use a valid TOTP code or recovery code.");
        }

        jdbc.update("""
                UPDATE users SET two_factor_enabled = false, two_factor_secret = NULL,
                    two_factor_recovery_codes = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, userId);
        authService.revokeAllUserTokens(userId);
        audit.event(userId, "two_factor_disabled", "user", userId, null);
        return Map.of("message",
                "2FA disabled successfully. All sessions have been revoked for security.");
    }

    /** {@code get_2fa_status}: enabled flag, code count, and issue date. */
    public TwoFactorStatusResponse status(long userId) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap("""
                    SELECT COALESCE(two_factor_enabled, false) AS enabled,
                           COALESCE(array_length(two_factor_recovery_codes, 1), 0) AS recovery_count
                    FROM users WHERE id = ?
                    """, userId);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return new TwoFactorStatusResponse(false, false, 0, null);
        }
        boolean enabled = Boolean.TRUE.equals(row.get("enabled"));
        int remaining = ((Number) row.get("recovery_count")).intValue();

        Instant generatedAt = null;
        if (enabled) {
            List<Timestamp> stamps = jdbc.queryForList("""
                    SELECT created_at FROM audit_logs
                    WHERE user_id = ?
                      AND action IN ('two_factor_enabled', 'two_factor_backup_codes_regenerated')
                    ORDER BY created_at DESC LIMIT 1
                    """, Timestamp.class, userId);
            if (!stamps.isEmpty() && stamps.get(0) != null) {
                generatedAt = stamps.get(0).toInstant();
            }
        }
        return new TwoFactorStatusResponse(enabled, remaining > 0, remaining, generatedAt);
    }

    /** {@code verify_2fa_code}: confirm a TOTP code against the stored secret. */
    public Map<String, Object> verify(long userId, TwoFactorVerifyRequest req) {
        if (req.code() == null || req.code().length() < 6 || req.code().length() > 20) {
            throw ApiError.badRequest("Invalid 2FA code");
        }
        Map<String, Object> user = getUser(userId);
        if (!Boolean.TRUE.equals(user.get("two_factor_enabled"))) {
            throw ApiError.badRequest("2FA is not enabled for this account");
        }
        String stored = (String) user.get("two_factor_secret");
        if (stored == null) {
            throw ApiError.internal("2FA secret missing");
        }
        String secret;
        try {
            secret = totpSecrets.open(stored);
        } catch (IllegalStateException e) {
            throw ApiError.internal(e.getMessage());
        }
        if (!totpVerifier.verify(secret, req.code())) {
            throw ApiError.unauthorized("Invalid 2FA code");
        }
        return Map.of("verified", true);
    }

    /** {@code regenerate_backup_codes}: TOTP-gated rotation of the code set. */
    public Map<String, Object> regenerateBackupCodes(long userId,
            RegenerateBackupCodesRequest req) {
        if (req.code() == null || req.code().length() < 6 || req.code().length() > 20) {
            throw ApiError.badRequest("Invalid 2FA code");
        }
        Map<String, Object> user = getUser(userId);
        if (!Boolean.TRUE.equals(user.get("two_factor_enabled"))) {
            throw ApiError.badRequest("2FA is not enabled for this account");
        }
        String stored = (String) user.get("two_factor_secret");
        if (stored == null) {
            throw ApiError.internal("2FA secret missing");
        }
        // Upstream verifies against the stored (possibly sealed) value through
        // verify_totp_code — decrypt first so the check sees the plaintext seed.
        String secret;
        try {
            secret = totpSecrets.open(stored);
        } catch (IllegalStateException e) {
            throw ApiError.internal(e.getMessage());
        }
        if (!totpVerifier.verify(secret, req.code())) {
            throw ApiError.badRequest("Invalid 2FA code");
        }

        List<String> newCodes = totpSecrets.generateBackupCodes();
        authService.updateRecoveryCodes(userId, newCodes);
        audit.event(userId, "two_factor_backup_codes_regenerated", "user", userId, null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Backup codes regenerated successfully");
        body.put("backup_codes", newCodes);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<String> recoveryCodes(Map<String, Object> user) {
        Object raw = user.get("two_factor_recovery_codes");
        if (raw instanceof java.sql.Array array) {
            try {
                Object elements = array.getArray();
                if (elements instanceof Object[] arr) {
                    List<String> out = new java.util.ArrayList<>(arr.length);
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
}
