package com.hotelapp.auth;

import com.hotelapp.consent.Consents;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Port of {@code AuthRepository::resolve_google_guest}: resolves a verified
 * Google identity to an active guest account inside one transaction.
 *
 * <p>The Google subject is the durable identity; email only links an unlinked
 * guest during the first successful sign-in. Concurrent first-time sign-ins
 * lose their unique-constraint race to a winner the same transaction then
 * hands back — see the savepoint + bounded-retry structure mirroring upstream.
 */
@Component
public class GoogleAuthTx {

    private static final int MAX_ATTEMPTS = 3;

    /** Consent evidence required to insert a new Google guest account. */
    public record NewAccountConsent(
            List<ConsentAcceptance> consents,
            Consents.Context context,
            String languagePreference) {
    }

    /** Outcome of resolving a Google identity to a guest user. */
    public record Resolution(Map<String, Object> user, boolean created, Long guestId) {
    }

    private static Resolution existingUser(Map<String, Object> user) {
        return new Resolution(user, false, null);
    }

    private static final String USER_COLUMNS =
            "id, username, email, google_subject, full_name, phone, is_active, is_verified,"
                    + " user_type, two_factor_enabled, two_factor_secret,"
                    + " two_factor_recovery_codes, guest_id, created_at, updated_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Consents consents;

    public GoogleAuthTx(JdbcTemplate jdbc, TransactionTemplate tx, Consents consents) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.consents = consents;
    }

    public Resolution resolve(GoogleIdentityService.Identity identity, NewAccountConsent newAccount) {
        return attempt(identity, 0, newAccount);
    }

    private Resolution attempt(GoogleIdentityService.Identity identity, int attempt,
            NewAccountConsent newAccount) {
        try {
            return tx.execute(status -> resolveInTx(identity, attempt, newAccount));
        } catch (RetryResolution e) {
            // The attempt's transaction just rolled back; a lost unique-constraint
            // race means a concurrent sign-in committed first, so a fresh attempt
            // resolves to the winner instead of surfacing a false conflict.
            return retry(identity, attempt, newAccount);
        }
    }

    private Resolution resolveInTx(GoogleIdentityService.Identity identity, int attempt,
            NewAccountConsent newAccount) {
        // Existing link wins outright.
        List<Map<String, Object>> bySubject = jdbc.queryForList(
                "SELECT " + USER_COLUMNS + " FROM users"
                        + " WHERE google_subject = ? AND deleted_at IS NULL FOR UPDATE",
                identity.subject());
        if (!bySubject.isEmpty()) {
            ensureActiveGoogleGuest(bySubject.get(0));
            return existingUser(bySubject.get(0));
        }

        List<Map<String, Object>> byEmail = jdbc.queryForList(
                "SELECT " + USER_COLUMNS + " FROM users"
                        + " WHERE email = ? AND deleted_at IS NULL FOR UPDATE",
                identity.email());
        if (!byEmail.isEmpty()) {
            Map<String, Object> user = byEmail.get(0);
            ensureActiveGoogleGuest(user);
            String linked = (String) user.get("google_subject");
            if (identity.subject().equals(linked)) {
                return existingUser(user);
            }
            if (linked != null) {
                throw ApiError.conflict(
                        "This guest account is already linked to a different sign-in method.");
            }

            jdbc.execute("SAVEPOINT google_subject_link");
            Map<String, Object> linkedUser;
            try {
                linkedUser = jdbc.queryForMap(
                        "UPDATE users SET google_subject = ?, is_verified = true,"
                                + " updated_at = CURRENT_TIMESTAMP"
                                + " WHERE id = ? AND google_subject IS NULL"
                                + " RETURNING " + USER_COLUMNS,
                        identity.subject(), ((Number) user.get("id")).longValue());
                jdbc.execute("RELEASE SAVEPOINT google_subject_link");
            } catch (DataIntegrityViolationException e) {
                if (!isConstraintViolation(e, "uq_users_google_subject")) {
                    throw e;
                }
                jdbc.execute("ROLLBACK TO SAVEPOINT google_subject_link");
                jdbc.execute("RELEASE SAVEPOINT google_subject_link");
                Map<String, Object> winner = findBySubject(identity);
                ensureActiveGoogleGuest(winner);
                return existingUser(winner);
            }
            return existingUser(linkedUser);
        }

        boolean emailReserved = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)",
                Boolean.class, identity.email()));
        if (emailReserved) {
            Map<String, Object> winner = findBySubjectOrNull(identity);
            if (winner != null) {
                ensureActiveGoogleGuest(winner);
                return existingUser(winner);
            }
            throw ApiError.conflict(
                    "This guest account cannot be linked to Google sign-in.");
        }

        if (newAccount == null) {
            throw ApiError.badRequest(
                    "Consent to the Booking Terms and Conditions is required before this request"
                            + " can be accepted");
        }
        Consents.validateLocales(newAccount.consents());
        Consents.requireConsents(newAccount.consents(), Consents.REGISTRATION_REQUIRED);

        jdbc.execute("SAVEPOINT google_guest_create");
        String guestFullName = guestFullName(identity, attempt);
        String displayName = GoogleIdentityService.displayName(identity);
        Long guestId = jdbc.query(
                "INSERT INTO guests (first_name, last_name, nick_name, email, is_active,"
                        + " guest_type, language_preference, created_at)"
                        + " VALUES (?, ?, ?, ?, true, 'non_member', ?, CURRENT_TIMESTAMP)"
                        + " ON CONFLICT DO NOTHING RETURNING id",
                rs -> rs.next() ? rs.getLong("id") : null,
                identity.givenName(), identity.familyName(), guestFullName,
                identity.email(), newAccount.languagePreference());
        if (guestId == null) {
            jdbc.execute("ROLLBACK TO SAVEPOINT google_guest_create");
            jdbc.execute("RELEASE SAVEPOINT google_guest_create");
            Map<String, Object> winner = findBySubjectOrNull(identity);
            if (winner != null) {
                ensureActiveGoogleGuest(winner);
                return existingUser(winner);
            }
            throw new RetryResolution();
        }

        String username = usernameForAttempt(identity, attempt);
        Map<String, Object> createdUser;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "INSERT INTO users (uuid, username, email, full_name, user_type, guest_id,"
                            + " is_active, is_verified, google_subject, created_at)"
                            + " VALUES (?::uuid, ?, ?, ?, 'guest', ?, true, true, ?,"
                            + " CURRENT_TIMESTAMP) ON CONFLICT (username) DO NOTHING"
                            + " RETURNING " + USER_COLUMNS,
                    UUID.randomUUID().toString(), username, identity.email(), displayName,
                    guestId, identity.subject());
            if (rows.isEmpty()) {
                jdbc.execute("ROLLBACK TO SAVEPOINT google_guest_create");
                jdbc.execute("RELEASE SAVEPOINT google_guest_create");
                throw new RetryResolution();
            }
            createdUser = rows.get(0);
            jdbc.execute("RELEASE SAVEPOINT google_guest_create");
        } catch (DataIntegrityViolationException e) {
            if (!isConstraintViolation(e, "uq_users_google_subject")
                    && !isConstraintViolation(e, "users_email_key")) {
                throw e;
            }
            jdbc.execute("ROLLBACK TO SAVEPOINT google_guest_create");
            jdbc.execute("RELEASE SAVEPOINT google_guest_create");
            throw new RetryResolution();
        }

        long userId = ((Number) createdUser.get("id")).longValue();
        Long guestRoleId;
        try {
            guestRoleId = jdbc.queryForObject(
                    "SELECT id FROM roles WHERE name = 'guest' LIMIT 1", Long.class);
        } catch (Exception e) {
            throw ApiError.database("Guest role not found: " + e.getMessage());
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)"
                + " ON CONFLICT DO NOTHING", userId, guestRoleId);

        consents.record(Consents.Subject.user(userId).withGuest(guestId),
                newAccount.consents(), Consents.Source.REGISTRATION, newAccount.context());

        return new Resolution(createdUser, true, guestId);
    }

    /** Fresh attempt in a new transaction; bounded like upstream. */
    private Resolution retry(GoogleIdentityService.Identity identity, int attempt,
            NewAccountConsent newAccount) {
        if (attempt + 1 >= MAX_ATTEMPTS) {
            throw ApiError.conflict(
                    "This Google account could not be linked to a guest account. Please try again.");
        }
        return attempt(identity, attempt + 1, newAccount);
    }

    private Map<String, Object> findBySubject(GoogleIdentityService.Identity identity) {
        Map<String, Object> winner = findBySubjectOrNull(identity);
        if (winner == null) {
            throw ApiError.conflict(
                    "This Google account is already linked to another guest account.");
        }
        return winner;
    }

    private Map<String, Object> findBySubjectOrNull(GoogleIdentityService.Identity identity) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + USER_COLUMNS + " FROM users"
                        + " WHERE google_subject = ? AND deleted_at IS NULL FOR UPDATE",
                identity.subject());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void ensureActiveGoogleGuest(Map<String, Object> user) {
        boolean active = Boolean.TRUE.equals(user.get("is_active"));
        if (!active || !"guest".equals(user.get("user_type"))) {
            throw ApiError.conflict(
                    "Google sign-in is available only for active guest accounts.");
        }
    }

    private static boolean isConstraintViolation(DataIntegrityViolationException e,
            String constraint) {
        String message = String.valueOf(e.getMessage());
        Throwable cause = e.getRootCause();
        String rootMessage = cause == null ? "" : String.valueOf(cause.getMessage());
        return message.contains(constraint) || rootMessage.contains(constraint);
    }

    /** {@code google_guest_full_name}: display name + deterministic suffix. */
    private static String guestFullName(GoogleIdentityService.Identity identity, int attempt) {
        String suffix = attempt == 0
                ? GoogleIdentityService.fingerprint(identity.email(), identity.subject())
                : UUID.randomUUID().toString().replace("-", "");
        suffix = " (Google " + suffix + ")";
        String display = GoogleIdentityService.displayName(identity);
        int maxDisplay = Math.max(0, 255 - suffix.length());
        if (display.length() > maxDisplay) {
            display = display.substring(0, maxDisplay);
        }
        return display + suffix;
    }

    /** {@code google_username_for_attempt}: deterministic first, uuid retry. */
    private static String usernameForAttempt(GoogleIdentityService.Identity identity, int attempt) {
        if (attempt == 0) {
            return GoogleIdentityService.usernameFor(identity.email(), identity.subject());
        }
        return "google_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** Internal control flow: unwind the current attempt and retry fresh. */
    private static final class RetryResolution extends RuntimeException {
    }
}
