package com.hotelapp.promotions;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * promotions::issue_welcome_deluxe_voucher — the non-public voucher issued
 * automatically when a guest portal account is activated or transferred.
 */
public final class WelcomeVouchers {

    private static final String SLUG = "welcome-deluxe-10";

    private WelcomeVouchers() {
    }

    public static void issue(JdbcTemplate jdbc, TransactionTemplate tx, AuditWriter audit,
            long guestId) {
        List<Map<String, Object>> promotions = jdbc.queryForList(
                "SELECT id, status, claim_starts_at, claim_ends_at, claim_limit,"
                        + " COALESCE(claimed_count, 0) AS claimed_count FROM promotions"
                        + " WHERE slug = ?",
                SLUG);
        if (promotions.isEmpty()) {
            throw ApiError.internal("Welcome Deluxe promotion is not configured");
        }
        Map<String, Object> promotion = promotions.get(0);
        if (!"published".equals(promotion.get("status"))) {
            throw ApiError.conflict("Only published promotions can issue vouchers");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (promotion.get("claim_starts_at") instanceof OffsetDateTime startsAt
                && startsAt.isAfter(now)) {
            throw ApiError.conflict("This promotion is not open for claims yet");
        }
        if (promotion.get("claim_ends_at") instanceof OffsetDateTime endsAt
                && endsAt.isBefore(now)) {
            throw ApiError.conflict("This promotion is no longer available");
        }
        if (promotion.get("claim_limit") instanceof Number limit
                && ((Number) promotion.get("claimed_count")).longValue() >= limit.longValue()) {
            throw ApiError.conflict("This promotion has reached its claim limit");
        }
        Boolean existing = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM vouchers
                    WHERE promotion_id = ? AND guest_id = ? AND status = 'available')
                """, Boolean.class, promotion.get("id"), guestId);
        if (Boolean.TRUE.equals(existing)) {
            return;
        }
        tx.executeWithoutResult(status -> {
            Long voucherId = jdbc.queryForObject("""
                    INSERT INTO vouchers (promotion_id, guest_id, code, status, source,
                        expires_at, issued_by, claimed_at)
                    VALUES (?, ?, ?, 'available', 'admin_issue', NULL, NULL,
                        CURRENT_TIMESTAMP)
                    ON CONFLICT (promotion_id, guest_id) DO NOTHING
                    RETURNING id
                    """, Long.class, promotion.get("id"), guestId, code());
            if (voucherId == null) {
                return;
            }
            int reserved = jdbc.update("""
                    UPDATE promotions
                    SET claimed_count = claimed_count + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'published'
                      AND (claim_limit IS NULL OR claimed_count < claim_limit)
                    """, promotion.get("id"));
            if (reserved == 0) {
                throw ApiError.conflict(
                        "The Welcome Deluxe promotion has reached its claim limit");
            }
            audit.event(null, "voucher.welcome_issued", "voucher", voucherId,
                    Map.of("promotion_id", promotion.get("id"), "guest_id", guestId,
                            "source", "guest_activation"));
        });
    }

    private static String code() {
        return "VCH" + UUID.randomUUID().toString()
                .replace("-", "").toUpperCase().substring(0, 20);
    }
}
