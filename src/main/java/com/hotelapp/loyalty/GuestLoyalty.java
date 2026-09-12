package com.hotelapp.loyalty;

import com.hotelapp.core.error.ApiError;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the guest-facing slice of {@code modules/loyalty} — membership
 * lookup and {@code redeem_reward_for_guest} for the July Deluxe loyalty
 * promotion claim path.
 */
@Component
public class GuestLoyalty {

    public static final String JULY_DELUXE_LOYALTY_REWARD_NAME = "July Deluxe Room 20% Voucher";
    public static final String JULY_DELUXE_PROMOTION_SLUG = "july-deluxe-20-loyalty";
    static final String JULY_DELUXE_VOUCHER_TERMS =
            "The voucher is issued immediately, may be used once, and is valid only for a"
                    + " Deluxe Room stay in July 2026.";

    private final JdbcTemplate jdbc;

    public GuestLoyalty(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // Repository rows
    // ------------------------------------------------------------------

    /** {@code LoyaltyMemberSummary} — only the fields the guest flows read. */
    public record MemberSummary(
            long id, long guestId, String status, long accountId,
            int availablePoints, long tierId) {
    }

    public MemberSummary memberByGuest(long guestId) {
        List<MemberSummary> rows = jdbc.query("""
                SELECT lm.id, lm.guest_id, lm.status, la.id AS account_id,
                    COALESCE((SELECT SUM(available_delta) FROM loyalty_transactions lt
                              WHERE lt.member_id = lm.id), 0) AS available_points,
                    ltier.id AS tier_id
                FROM loyalty_members lm
                JOIN guests g ON g.id = lm.guest_id
                JOIN loyalty_accounts la ON la.member_id = lm.id
                JOIN loyalty_tiers ltier ON ltier.id = la.current_tier_id
                WHERE lm.guest_id = ?
                """, (rs, i) -> new MemberSummary(
                rs.getLong("id"), rs.getLong("guest_id"), rs.getString("status"),
                rs.getLong("account_id"), rs.getInt("available_points"),
                rs.getLong("tier_id")), guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code LoyaltyReward} — the fields the redeem flow reads. */
    public record RewardRow(
            long id, String name, String category, int pointsCost, Long minimumTierId,
            boolean requiresApproval, boolean isActive, Integer inventoryCount,
            LocalDate validFrom, LocalDate validTo, String termsConditions) {
    }

    private static RewardRow rewardFromRow(Map<String, Object> row) {
        return new RewardRow(
                ((Number) row.get("id")).longValue(),
                (String) row.get("name"),
                (String) row.get("category"),
                row.get("points_cost") instanceof Number n ? n.intValue() : 0,
                row.get("minimum_tier_id") instanceof Number n ? n.longValue() : null,
                Boolean.TRUE.equals(row.get("requires_approval")),
                Boolean.TRUE.equals(row.get("is_active")),
                row.get("inventory_count") instanceof Number n ? n.intValue() : null,
                row.get("valid_from") instanceof java.sql.Date d ? d.toLocalDate() : null,
                row.get("valid_to") instanceof java.sql.Date d ? d.toLocalDate() : null,
                (String) row.get("terms_conditions"));
    }

    private static final String REWARD_COLS = """
            lr.id, lr.name, lr.description, lr.category, lr.points_cost,
            lr.minimum_tier_id, lt.name AS minimum_tier_name, lr.requires_approval,
            lr.is_active, lr.inventory_count, lr.valid_from, lr.valid_to,
            lr.terms_conditions, lr.created_at, lr.updated_at
            """;

    public RewardRow findActiveRewardByName(String name) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + REWARD_COLS + " FROM loyalty_rewards lr"
                        + " LEFT JOIN loyalty_tiers lt ON lt.id = lr.minimum_tier_id"
                        + " WHERE lr.name = ? AND lr.is_active = true ORDER BY lr.id LIMIT 1",
                name);
        return rows.isEmpty() ? null : rewardFromRow(rows.get(0));
    }

    private RewardRow findReward(long rewardId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + REWARD_COLS + " FROM loyalty_rewards lr"
                        + " LEFT JOIN loyalty_tiers lt ON lt.id = lr.minimum_tier_id"
                        + " WHERE lr.id = ?",
                rewardId);
        return rows.isEmpty() ? null : rewardFromRow(rows.get(0));
    }

    private boolean redemptionApprovalRequired() {
        Boolean value = jdbc.queryForObject(
                "SELECT redemption_approval_required FROM loyalty_program_rules WHERE id = 1",
                Boolean.class);
        return Boolean.TRUE.equals(value);
    }

    // ------------------------------------------------------------------
    // redeem_reward_for_guest
    // ------------------------------------------------------------------

    /**
     * {@code redeem_reward_for_guest}: a guest redeems a loyalty reward. For
     * the July Deluxe voucher the same transaction also issues the voucher —
     * approval bypass applies only to that campaign.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long redeemRewardForGuest(long guestId, long rewardId,
            com.hotelapp.promotions.GuestPromotions promotions) {
        MemberSummary member = activeMemberForGuest(guestId);

        RewardRow reward = findReward(rewardId);
        if (reward == null) {
            throw ApiError.notFound("Reward not found.");
        }
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        validateRewardRedeemable(reward, today);
        if (reward.minimumTierId() != null && reward.minimumTierId() > member.tierId()) {
            throw ApiError.badRequest("This reward requires a higher loyalty tier.");
        }
        if (member.availablePoints() < reward.pointsCost()) {
            throw ApiError.badRequest("Insufficient points. Required: " + reward.pointsCost()
                    + ", available: " + member.availablePoints() + ".");
        }

        Long julyDeluxePromotionId = null;
        if (JULY_DELUXE_LOYALTY_REWARD_NAME.equals(reward.name())
                && "discount".equals(reward.category())
                && reward.pointsCost() == 2_000
                && JULY_DELUXE_VOUCHER_TERMS.equals(reward.termsConditions())) {
            var promotion = promotions.findPromotionBySlug(JULY_DELUXE_PROMOTION_SLUG);
            if (promotion == null) {
                throw ApiError.internal("July Deluxe voucher promotion is not configured.");
            }
            if (!"published".equals(promotion.status())) {
                throw ApiError.conflict("The July Deluxe voucher is not currently available.");
            }
            if (promotions.guestHasVoucher(promotion.id(), guestId)) {
                throw ApiError.conflict("You have already claimed the July Deluxe voucher.");
            }
            julyDeluxePromotionId = promotion.id();
        }
        boolean requiresApproval = julyDeluxePromotionId == null
                && (reward.requiresApproval() || redemptionApprovalRequired());
        String status = requiresApproval ? "pending" : "approved";

        long balanceBefore = jdbc.queryForObject(
                "SELECT COALESCE(SUM(available_delta), 0) FROM loyalty_transactions"
                        + " WHERE member_id = ?",
                Long.class, member.id());
        long balanceAfter = balanceBefore - reward.pointsCost();
        if (balanceAfter > Integer.MAX_VALUE || balanceAfter < Integer.MIN_VALUE) {
            throw ApiError.badRequest("Loyalty points balance is out of range.");
        }
        Long transactionId = jdbc.queryForObject("""
                INSERT INTO loyalty_transactions (
                    member_id, account_id, transaction_type, points_delta, available_delta,
                    balance_after, source_type, source_id, booking_id, payment_id, invoice_id,
                    related_transaction_id, description, metadata, actor_user_id)
                VALUES (?, ?, 'redeemed', ?, ?, ?, 'reward', ?, NULL, NULL, NULL, NULL, ?,
                    CAST(? AS jsonb), NULL)
                RETURNING id
                """, Long.class, member.id(), member.accountId(), -reward.pointsCost(),
                -reward.pointsCost(), (int) balanceAfter, reward.id,
                "Redeemed reward: " + reward.name(),
                "{\"reward_name\": " + jsonString(reward.name())
                        + ", \"requires_approval\": " + requiresApproval + "}");

        Long redemptionId = jdbc.queryForObject("""
                INSERT INTO loyalty_redemptions (
                    member_id, reward_id, transaction_id, points_spent, status, notes,
                    reviewed_at)
                VALUES (?, ?, ?, ?, ?, NULL,
                    CASE WHEN ? = 'approved' THEN CURRENT_TIMESTAMP ELSE NULL END)
                RETURNING id
                """, Long.class, member.id(), reward.id(), transactionId, reward.pointsCost(),
                status, status);

        if (julyDeluxePromotionId != null) {
            Long voucherId = promotions.insertVoucherIfNew(julyDeluxePromotionId, guestId,
                    com.hotelapp.promotions.GuestPromotions.generateVoucherCode(),
                    "guest_claim", null, null);
            if (voucherId == null || !promotions.reserveClaimCapacity(julyDeluxePromotionId)) {
                throw ApiError.conflict("The July Deluxe voucher is no longer available.");
            }
        }
        if (reward.inventoryCount() != null) {
            jdbc.update("""
                    UPDATE loyalty_rewards
                    SET inventory_count = inventory_count - 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND inventory_count IS NOT NULL
                    """, reward.id());
        }

        Long found = jdbc.query(
                "SELECT id FROM loyalty_redemptions WHERE id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, redemptionId);
        if (found == null) {
            throw ApiError.internal("Created redemption was not found.");
        }
        return found;
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private MemberSummary activeMemberForGuest(long guestId) {
        MemberSummary member = memberByGuest(guestId);
        if (member == null) {
            throw ApiError.notFound("No loyalty membership found.");
        }
        if (!"active".equals(member.status())) {
            throw ApiError.badRequest("Loyalty membership is " + member.status() + ".");
        }
        return member;
    }

    private static void validateRewardRedeemable(RewardRow reward, LocalDate today) {
        if (!reward.isActive()) {
            throw ApiError.badRequest("Reward is inactive.");
        }
        if (reward.inventoryCount() != null && reward.inventoryCount() <= 0) {
            throw ApiError.badRequest("Reward is out of stock.");
        }
        if (reward.validFrom() != null && reward.validFrom().isAfter(today)) {
            throw ApiError.badRequest("Reward is not available yet.");
        }
        if (reward.validTo() != null && reward.validTo().isBefore(today)) {
            throw ApiError.badRequest("Reward has expired.");
        }
    }
}
