package com.hotelapp.loyalty;

import com.hotelapp.core.error.ApiError;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Port of {@code modules::loyalty::service::award_eligible_booking_points} —
 * post-commit payment points awarding. Callers treat every failure as
 * best-effort, exactly like upstream's `if let Err` wrappers.
 */
@Component
public class LoyaltyAwards {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyAwards.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;

    public LoyaltyAwards(JdbcTemplate jdbc, TransactionTemplate txTemplate) {
        this.jdbc = jdbc;
        this.txTemplate = txTemplate;
    }

    /** {@code payment_award_candidates}' candidate row. */
    private record AwardCandidate(long bookingId, long guestId, long paymentId,
            Long invoiceId, BigDecimal amount, long nights) {
    }

    /** {@code award_eligible_booking_points}. */
    public List<String> awardEligibleBookingPoints(long bookingId, Long paymentId,
            Long actorUserId) {
        Map<String, Object> rules = jdbc.queryForMap("""
                SELECT points_per_currency_unit, tier_qualification_metric,
                       earning_enabled, min_eligible_amount
                FROM loyalty_program_rules WHERE id = 1
                """);
        if (!Boolean.TRUE.equals(rules.get("earning_enabled"))) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
                SELECT
                    b.id AS booking_id,
                    b.guest_id,
                    p.id AS payment_id,
                    (SELECT i.id FROM invoices i WHERE i.booking_id = b.id
                     ORDER BY i.id DESC LIMIT 1) AS invoice_id,
                    p.amount::text AS amount,
                    GREATEST((b.check_out_date - b.check_in_date), 1) AS nights
                FROM payments p
                JOIN bookings b ON b.id = p.booking_id
                WHERE p.status = 'completed'
                  AND COALESCE(p.payment_type, 'booking') IN ('booking', 'room_charge')
                  AND b.status IN ('checked_out', 'completed')
                  AND COALESCE(b.is_complimentary, false) = false
                  AND b.id = ?
                """);
        List<Object> params = new java.util.ArrayList<>(List.of(bookingId));
        if (paymentId != null) {
            sql.append(" AND p.id = ?");
            params.add(paymentId);
        }
        sql.append(" ORDER BY p.created_at ASC, p.id ASC");

        List<AwardCandidate> candidates = jdbc.query(sql.toString(),
                (rs, i) -> new AwardCandidate(
                        rs.getLong("booking_id"),
                        rs.getLong("guest_id"),
                        rs.getLong("payment_id"),
                        rs.getObject("invoice_id") == null ? null : rs.getLong("invoice_id"),
                        rs.getBigDecimal("amount"),
                        rs.getLong("nights")),
                params.toArray());

        List<String> results = new java.util.ArrayList<>();
        for (AwardCandidate candidate : candidates) {
            results.add(awardCandidate(rules, candidate, actorUserId));
        }
        return results;
    }

    /** {@code award_payment_candidate}. */
    private String awardCandidate(Map<String, Object> rules, AwardCandidate candidate,
            Long actorUserId) {
        BigDecimal minEligible = (BigDecimal) rules.get("min_eligible_amount");
        if (candidate.amount().compareTo(minEligible) < 0) {
            return "Payment is below the minimum eligible amount.";
        }

        // member_summary_sql inner-joins loyalty_tiers on current_tier_id, so a
        // member whose account tier is dangling is "not enrolled" upstream too.
        List<Map<String, Object>> members = jdbc.queryForList("""
                SELECT lm.id, lm.status, la.id AS account_id, la.qualifying_points,
                       la.qualifying_nights, la.qualifying_spend, la.current_tier_id
                FROM loyalty_members lm
                JOIN loyalty_accounts la ON la.member_id = lm.id
                JOIN loyalty_tiers ltier ON ltier.id = la.current_tier_id
                WHERE lm.guest_id = ?
                """, candidate.guestId());
        if (members.isEmpty()) {
            return "Guest is not enrolled in loyalty.";
        }
        Map<String, Object> member = members.get(0);
        if (!"active".equals(String.valueOf(member.get("status")))) {
            return "Member is " + member.get("status") + ".";
        }
        long memberId = ((Number) member.get("id")).longValue();

        Boolean alreadyAwarded = jdbc.queryForObject("""
                SELECT COUNT(*) > 0 FROM loyalty_transactions
                WHERE member_id = ? AND source_type = 'payment' AND source_id = ?
                  AND transaction_type = 'earned'
                """, Boolean.class, memberId, candidate.paymentId());
        if (Boolean.TRUE.equals(alreadyAwarded)) {
            return "Points already awarded for this payment.";
        }

        BigDecimal perUnit = (BigDecimal) rules.get("points_per_currency_unit");
        int points = candidate.amount().multiply(perUnit)
                .setScale(0, java.math.RoundingMode.FLOOR).intValue();
        if (points <= 0) {
            return "Eligible payment produced zero points.";
        }

        Long priorBookingAwards = jdbc.queryForObject("""
                SELECT COUNT(*) FROM loyalty_transactions
                WHERE member_id = ? AND booking_id = ? AND transaction_type = 'earned'
                """, Long.class, memberId, candidate.bookingId());
        long nightsIncrement = (priorBookingAwards == null || priorBookingAwards == 0)
                ? candidate.nights() : 0;

        String metric = String.valueOf(rules.get("tier_qualification_metric"));
        double metricValueAfter = switch (metric) {
            case "nights" -> ((Number) member.get("qualifying_nights")).doubleValue()
                    + nightsIncrement;
            case "spend" -> ((Number) member.get("qualifying_spend")).doubleValue()
                    + candidate.amount().doubleValue();
            default -> ((Number) member.get("qualifying_points")).doubleValue() + points;
        };
        String orderColumn = switch (metric) {
            case "nights" -> "min_nights";
            case "spend" -> "min_spend";
            default -> "min_points";
        };
        // Upstream `best_tier_for_metric` fetches one row — an empty result is
        // a 404 that fails the award (warn-logged by the caller), not a silent
        // fallback to the member's current tier.
        List<Long> newTierIds = jdbc.query(
                "SELECT id FROM loyalty_tiers WHERE is_active = true AND "
                        + orderColumn + " <= ? ORDER BY " + orderColumn + " DESC, "
                        + "sort_order DESC LIMIT 1",
                (rs, i) -> rs.getLong(1), metricValueAfter);
        if (newTierIds.isEmpty()) {
            throw ApiError.notFound("Resource not found");
        }
        long newTierId = newTierIds.get(0);

        long accountId = ((Number) member.get("account_id")).longValue();
        txTemplate.executeWithoutResult(tx -> {
            Long balanceBefore = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(available_delta), 0) FROM loyalty_transactions "
                            + "WHERE member_id = ?",
                    Long.class, memberId);
            long balanceAfter = (balanceBefore == null ? 0 : balanceBefore) + points;
            if (balanceAfter > Integer.MAX_VALUE || balanceAfter < Integer.MIN_VALUE) {
                throw ApiError.badRequest("Loyalty points balance is out of range.");
            }
            jdbc.update("""
                    INSERT INTO loyalty_transactions (
                        member_id, account_id, transaction_type, points_delta, available_delta,
                        balance_after, source_type, source_id, booking_id, payment_id, invoice_id,
                        related_transaction_id, description, metadata, actor_user_id
                    )
                    VALUES (?, ?, 'earned', ?, ?, ?, 'payment', ?, ?, ?, ?, NULL,
                            'Points earned from eligible stay payment', CAST(? AS jsonb), ?)
                    """, memberId, accountId, points, points, balanceAfter,
                    candidate.paymentId(), candidate.bookingId(), candidate.paymentId(),
                    candidate.invoiceId(),
                    "{\"amount\":" + candidate.amount() + ",\"nights_increment\":"
                            + nightsIncrement + ",\"tier_metric\":\"" + metric + "\"}",
                    actorUserId);
            jdbc.update("""
                    UPDATE loyalty_accounts
                    SET lifetime_points = lifetime_points + ?,
                        qualifying_points = qualifying_points + ?,
                        qualifying_spend = qualifying_spend + ?,
                        qualifying_nights = qualifying_nights + ?,
                        current_tier_id = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, points, points, candidate.amount(), nightsIncrement,
                    newTierId, accountId);
        });
        return null;
    }

    /** Best-effort wrapper mirroring upstream's `if let Err` warn-and-continue. */
    public void tryAwardEligibleBookingPoints(long bookingId, Long paymentId,
            Long actorUserId) {
        try {
            awardEligibleBookingPoints(bookingId, paymentId, actorUserId);
        } catch (Exception e) {
            log.warn("Failed to award loyalty points for payment {} on booking {}: {}",
                    paymentId, bookingId, e.getMessage());
        }
    }
}
