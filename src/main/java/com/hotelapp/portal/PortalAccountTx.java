package com.hotelapp.portal;

import com.hotelapp.consent.Consents;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The account-claim transaction ({@code claim_guest_account} +
 * {@code consent_service::record_tx}): guest row lock, account disposition,
 * upgrade-or-insert, guest role grant and consent persistence, all in one tx.
 * Kept as a separate bean so {@code PortalService} invokes it through the
 * Spring proxy, matching upstream's {@code pool.begin()}/{@code tx.commit()}.
 */
@Component
public class PortalAccountTx {

    private final JdbcTemplate jdbc;
    private final Consents consents;

    public PortalAccountTx(JdbcTemplate jdbc, Consents consents) {
        this.jdbc = jdbc;
        this.consents = consents;
    }

    public record ClaimOutcome(long userId, String username, boolean upgradedAnchorAccount) {
    }

    @Transactional
    public ClaimOutcome claim(long guestId, String username, String accountEmail,
            String passwordHash, String fullName, String phone, boolean isVerified,
            List<ConsentAcceptance> acceptances, Consents.Context context) {
        // lock_guest_for_claim — serialize competing claims on one guest.
        jdbc.queryForList("SELECT id FROM guests WHERE id = ? FOR UPDATE", guestId);

        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, is_active, (password_hash IS NOT NULL) AS has_password,
                       (deleted_at IS NOT NULL) AS is_deleted
                FROM users WHERE guest_id = ? AND user_type::text = 'guest'
                ORDER BY id LIMIT 1
                """, guestId);

        Long existingUserId = null;
        if (!existing.isEmpty()) {
            Map<String, Object> account = existing.get(0);
            boolean isDeleted = Boolean.TRUE.equals(account.get("is_deleted"));
            boolean hasPassword = Boolean.TRUE.equals(account.get("has_password"));
            boolean isActive = Boolean.TRUE.equals(account.get("is_active"));
            if (isDeleted || (hasPassword && !isActive)) {
                throw ApiError.conflict(
                        "This guest profile has a disabled account. "
                                + "Please contact the front desk.");
            }
            if (hasPassword) {
                throw ApiError.conflict(
                        "An account already exists for this booking. Please sign in, or use "
                                + "'forgot password' if you cannot get in.");
            }
            existingUserId = ((Number) account.get("id")).longValue();
        }

        long userId;
        if (existingUserId != null) {
            Long updated = jdbc.queryForObject("""
                    UPDATE users SET username = ?, email = ?, password_hash = ?,
                        full_name = COALESCE(?, full_name), phone = COALESCE(?, phone),
                        is_active = true, is_verified = ?,
                        password_changed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? RETURNING id
                    """, Long.class, username, accountEmail, passwordHash, fullName, phone,
                    isVerified, existingUserId);
            userId = updated;
        } else {
            Long inserted = jdbc.queryForObject("""
                    INSERT INTO users (username, email, password_hash, full_name, phone,
                        user_type, guest_id, is_active, is_verified, created_at)
                    VALUES (?, ?, ?, ?, ?, 'guest', ?, true, ?, CURRENT_TIMESTAMP)
                    RETURNING id
                    """, Long.class, username, accountEmail, passwordHash, fullName, phone,
                    guestId, isVerified);
            userId = inserted;
        }

        Long roleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE name = 'guest' LIMIT 1", Long.class);
        if (roleId == null) {
            throw ApiError.internal("Guest role not found");
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", userId, roleId);

        consents.record(Consents.Subject.user(userId).withGuest(guestId), acceptances,
                Consents.Source.REGISTRATION, context);

        return new ClaimOutcome(userId, username, existingUserId != null);
    }
}
