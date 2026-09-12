package com.hotelapp.promotions;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.loyalty.GuestLoyalty;
import com.hotelapp.promotions.PromotionModels.GuestPromotion;
import com.hotelapp.promotions.PromotionModels.GuestPromotionListResponse;
import com.hotelapp.promotions.PromotionModels.PublicPromotion;
import com.hotelapp.promotions.PromotionModels.Voucher;
import com.hotelapp.promotions.PromotionModels.VoucherListResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of the guest-facing slice of {@code modules/promotions}: the public
 * catalogue read, guest promotion listing (including the July Deluxe loyalty
 * offer), guest voucher listing and the idempotent promotion claim.
 */
@Component
public class GuestPromotions {

    public static final String WELCOME_DELUXE_PROMOTION_SLUG = "welcome-deluxe-10";
    public static final String JULY_DELUXE_LOYALTY_PROMOTION_SLUG = "july-deluxe-20-loyalty";

    private static final String PROMOTION_COLS = """
            p.id, p.slug, p.name, p.description, p.terms, p.status, p.promotion_kind,
            p.discount_type, p.discount_value, p.max_discount_amount, p.currency,
            p.claim_starts_at, p.claim_ends_at, p.stay_starts_on, p.stay_ends_on,
            p.min_nights, p.max_nights, p.min_subtotal, p.claim_limit, p.claimed_count,
            p.per_guest_limit, p.is_public, p.is_cancellable,
            CAST(p.version AS BIGINT) AS version, p.created_at, p.updated_at
            """;

    private static final String VOUCHER_COLS = """
            v.id, v.promotion_id, v.guest_id, p.name AS promotion_name,
            p.slug AS promotion_slug, v.code, v.status, v.source, v.expires_at,
            v.claimed_at, v.redeemed_at, v.revoked_at, v.created_at
            """;

    private final JdbcTemplate jdbc;
    private final GuestLoyalty loyalty;

    public GuestPromotions(JdbcTemplate jdbc, GuestLoyalty loyalty) {
        this.jdbc = jdbc;
        this.loyalty = loyalty;
    }

    /** {@code normalize_pagination(page, page_size, 20, 100)}. */
    private static long[] pagination(Long page, Long pageSize) {
        long p = page == null ? 1 : Math.max(page, 1);
        long size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        return new long[] {p, size, (p - 1) * size};
    }

    /** {@code request_id_is_valid}. */
    static void requestIdIsValid(String value) {
        if (value != null && (value.trim().isEmpty() || value.codePointCount(0, value.length()) > 128)) {
            throw ApiError.badRequest("Invalid client request identifier");
        }
    }

    public static String generateVoucherCode() {
        String random = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return "VCH" + random.substring(0, 20);
    }

    static String maskVoucherCode(String code) {
        if (code == null || code.isEmpty()) {
            return "••••";
        }
        int[] points = code.codePoints().toArray();
        int start = Math.max(0, points.length - 4);
        return "••••" + new String(points, start, points.length - start);
    }

    // ------------------------------------------------------------------
    // Repository: promotions
    // ------------------------------------------------------------------

    private PublicPromotion publicPromotionFromRow(Map<String, Object> row) {
        long promotionId = ((Number) row.get("id")).longValue();
        List<Long> roomTypeIds = jdbc.query(
                "SELECT room_type_id FROM promotion_room_types"
                        + " WHERE promotion_id = ? ORDER BY room_type_id",
                (rs, i) -> rs.getLong(1), promotionId);
        return new PublicPromotion(
                promotionId,
                (String) row.get("slug"),
                (String) row.get("name"),
                (String) row.get("description"),
                (String) row.get("terms"),
                (String) row.get("status"),
                (String) row.get("promotion_kind"),
                (String) row.get("discount_type"),
                (BigDecimal) row.get("discount_value"),
                (BigDecimal) row.get("max_discount_amount"),
                row.get("currency") != null ? (String) row.get("currency") : "USD",
                (OffsetDateTime) row.get("claim_starts_at"),
                (OffsetDateTime) row.get("claim_ends_at"),
                row.get("stay_starts_on") instanceof java.sql.Date d ? d.toLocalDate() : null,
                row.get("stay_ends_on") instanceof java.sql.Date d ? d.toLocalDate() : null,
                (Integer) row.get("min_nights"),
                (Integer) row.get("max_nights"),
                (BigDecimal) row.get("min_subtotal"),
                row.get("claim_limit") instanceof Number n ? n.longValue() : null,
                row.get("claimed_count") instanceof Number n ? n.longValue() : 0,
                row.get("per_guest_limit") instanceof Number n ? n.intValue() : 1,
                Boolean.TRUE.equals(row.get("is_public")),
                Boolean.TRUE.equals(row.get("is_cancellable")),
                roomTypeIds,
                row.get("version") instanceof Number n ? n.longValue() : 1,
                (OffsetDateTime) row.get("created_at"),
                (OffsetDateTime) row.get("updated_at"));
    }

    private List<PublicPromotion> publicPromotionsFromRows(List<Map<String, Object>> rows) {
        List<PublicPromotion> promotions = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            promotions.add(publicPromotionFromRow(row));
        }
        return promotions;
    }

    /** {@code PromotionRepository::list_public}: the active public catalogue page. */
    public record PublicPage(List<PublicPromotion> items, long total) {
    }

    private PublicPage listPublic(long pageSize, long offset) {
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM promotions p
                WHERE p.status = 'published' AND p.is_public = true
                  AND (p.claim_starts_at IS NULL OR p.claim_starts_at <= CURRENT_TIMESTAMP)
                  AND (p.claim_ends_at IS NULL OR p.claim_ends_at >= CURRENT_TIMESTAMP)
                  AND (p.claim_limit IS NULL OR p.claimed_count < p.claim_limit)
                """, Long.class);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT %s FROM promotions p
                WHERE p.status = 'published' AND p.is_public = true
                  AND (p.claim_starts_at IS NULL OR p.claim_starts_at <= CURRENT_TIMESTAMP)
                  AND (p.claim_ends_at IS NULL OR p.claim_ends_at >= CURRENT_TIMESTAMP)
                  AND (p.claim_limit IS NULL OR p.claimed_count < p.claim_limit)
                ORDER BY p.claim_ends_at NULLS LAST, p.created_at DESC
                LIMIT ? OFFSET ?
                """.formatted(PROMOTION_COLS), pageSize, offset);
        return new PublicPage(publicPromotionsFromRows(rows), total);
    }

    /** Internal promotion row for claim-path checks (room types not needed). */
    public record PromotionRow(
            long id, String slug, String status, boolean isPublic,
            OffsetDateTime claimStartsAt, OffsetDateTime claimEndsAt,
            Long claimLimit, long claimedCount, long version) {
    }

    private PromotionRow promotionRow(Map<String, Object> row) {
        return new PromotionRow(
                ((Number) row.get("id")).longValue(),
                (String) row.get("slug"),
                (String) row.get("status"),
                Boolean.TRUE.equals(row.get("is_public")),
                (OffsetDateTime) row.get("claim_starts_at"),
                (OffsetDateTime) row.get("claim_ends_at"),
                row.get("claim_limit") instanceof Number n ? n.longValue() : null,
                row.get("claimed_count") instanceof Number n ? n.longValue() : 0,
                row.get("version") instanceof Number n ? n.longValue() : 1);
    }

    /** {@code find_by_id_tx} — read inside the caller's transaction. */
    public PromotionRow findPromotionById(long promotionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + PROMOTION_COLS + " FROM promotions p WHERE p.id = ?", promotionId);
        return rows.isEmpty() ? null : promotionRow(rows.get(0));
    }

    public PromotionRow findPromotionBySlug(String slug) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + PROMOTION_COLS + " FROM promotions p WHERE p.slug = ?", slug);
        return rows.isEmpty() ? null : promotionRow(rows.get(0));
    }

    public boolean guestHasVoucher(long promotionId, long guestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM vouchers WHERE promotion_id = ? AND guest_id = ?)",
                Boolean.class, promotionId, guestId));
    }

    // ------------------------------------------------------------------
    // Repository: vouchers
    // ------------------------------------------------------------------

    private Voucher voucherFromRow(Map<String, Object> row, boolean includeCode) {
        String rawCode = (String) row.get("code");
        return new Voucher(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("promotion_id")).longValue(),
                ((Number) row.get("guest_id")).longValue(),
                (String) row.get("promotion_name"),
                (String) row.get("promotion_slug"),
                includeCode ? rawCode : null,
                maskVoucherCode(rawCode),
                (String) row.get("status"),
                (String) row.get("source"),
                (OffsetDateTime) row.get("expires_at"),
                (OffsetDateTime) row.get("claimed_at"),
                (OffsetDateTime) row.get("redeemed_at"),
                (OffsetDateTime) row.get("revoked_at"),
                (OffsetDateTime) row.get("created_at"));
    }

    Voucher findVoucherByPromotionGuest(long promotionId, long guestId, boolean includeCode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + VOUCHER_COLS + " FROM vouchers v"
                        + " JOIN promotions p ON p.id = v.promotion_id"
                        + " WHERE v.promotion_id = ? AND v.guest_id = ?",
                promotionId, guestId);
        return rows.isEmpty() ? null : voucherFromRow(rows.get(0), includeCode);
    }

    Voucher findVoucherForGuest(long voucherId, long guestId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + VOUCHER_COLS + " FROM vouchers v"
                        + " JOIN promotions p ON p.id = v.promotion_id"
                        + " WHERE v.id = ? AND v.guest_id = ?",
                voucherId, guestId);
        return rows.isEmpty() ? null : voucherFromRow(rows.get(0), true);
    }

    public Long insertVoucherIfNew(long promotionId, long guestId, String code, String source,
            OffsetDateTime expiresAt, Long issuedBy) {
        List<Long> ids = jdbc.query("""
                INSERT INTO vouchers (promotion_id, guest_id, code, status, source,
                    expires_at, issued_by, claimed_at)
                VALUES (?, ?, ?, 'available', ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (promotion_id, guest_id) DO NOTHING
                RETURNING id
                """, (rs, i) -> rs.getLong(1), promotionId, guestId, code, source,
                expiresAt, issuedBy);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public boolean reserveClaimCapacity(long promotionId) {
        return jdbc.update("""
                UPDATE promotions
                SET claimed_count = claimed_count + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'published'
                  AND (claim_limit IS NULL OR claimed_count < claim_limit)
                """, promotionId) == 1;
    }

    // ------------------------------------------------------------------
    // Service: public catalogue
    // ------------------------------------------------------------------

    /** {@code list_public_promotions}. */
    public PromotionModels.PublicPromotionListResponse listPublicPromotions(Long page,
            Long pageSize) {
        long[] p = pagination(page, pageSize);
        PublicPage publicPage = listPublic(p[1], p[2]);
        return new PromotionModels.PublicPromotionListResponse(
                publicPage.items(), publicPage.total(), p[0], p[1]);
    }

    /** {@code get_public_promotion} — public lookup is by slug. */
    public PublicPromotion getPublicPromotion(String slug) {
        String normalized = PromotionValidation.normalizeSlug(slug);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT %s FROM promotions p
                WHERE p.slug = ?
                  AND p.status = 'published' AND p.is_public = true
                  AND (p.claim_starts_at IS NULL OR p.claim_starts_at <= CURRENT_TIMESTAMP)
                  AND (p.claim_ends_at IS NULL OR p.claim_ends_at >= CURRENT_TIMESTAMP)
                """.formatted(PROMOTION_COLS), normalized);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Promotion not found");
        }
        return publicPromotionFromRow(rows.get(0));
    }

    // ------------------------------------------------------------------
    // Service: guest surfaces
    // ------------------------------------------------------------------

    /** {@code promotion_is_within_claim_window}. */
    static void ensureWithinClaimWindow(PromotionRow promotion) {
        OffsetDateTime now = OffsetDateTime.now();
        if (promotion.claimStartsAt() != null && promotion.claimStartsAt().isAfter(now)) {
            throw ApiError.conflict("This promotion is not open for claims yet");
        }
        if (promotion.claimEndsAt() != null && promotion.claimEndsAt().isBefore(now)) {
            throw ApiError.conflict("This promotion is no longer available");
        }
        if (promotion.claimLimit() != null && promotion.claimedCount() >= promotion.claimLimit()) {
            throw ApiError.conflict("This promotion has reached its claim limit");
        }
    }

    /** {@code ensure_guest_claimable}. */
    static void ensureGuestClaimable(PromotionRow promotion) {
        if (JULY_DELUXE_LOYALTY_PROMOTION_SLUG.equals(promotion.slug())) {
            throw ApiError.conflict("Redeem loyalty points to claim this voucher.");
        }
        if (!"published".equals(promotion.status()) || !promotion.isPublic()) {
            throw ApiError.notFound("Promotion not found");
        }
        ensureWithinClaimWindow(promotion);
    }

    /** {@code list_guest_promotions}. */
    public GuestPromotionListResponse listGuestPromotions(long guestId, Long page,
            Long pageSize) {
        long[] p = pagination(page, pageSize);
        PublicPage publicPage = listPublic(p[1], p[2]);
        List<GuestPromotion> items = new ArrayList<>(publicPage.items().size());
        for (PublicPromotion promotion : publicPage.items()) {
            boolean hasVoucher = guestHasVoucher(promotion.id(), guestId);
            items.add(new GuestPromotion(promotion, !hasVoucher, hasVoucher, null));
        }
        boolean hasLoyaltyOffer = false;
        GuestLoyalty.MemberSummary member = loyalty.memberByGuest(guestId);
        if (member != null && "active".equals(member.status())) {
            PromotionRow promotion = findPromotionBySlug(JULY_DELUXE_LOYALTY_PROMOTION_SLUG);
            if (promotion != null && isWithinClaimWindow(promotion)) {
                boolean hasVoucher = guestHasVoucher(promotion.id(), guestId);
                GuestLoyalty.RewardRow reward =
                        loyalty.findActiveRewardByName(GuestLoyalty.JULY_DELUXE_LOYALTY_REWARD_NAME);
                boolean canClaim = reward != null && !hasVoucher
                        && member.availablePoints() >= reward.pointsCost();
                String reason;
                if (hasVoucher) {
                    reason = null;
                } else if (reward != null) {
                    reason = member.availablePoints() < reward.pointsCost()
                            ? "You need " + reward.pointsCost()
                                    + " loyalty points to redeem this voucher."
                            : null;
                } else {
                    reason = "This loyalty voucher is not configured.";
                }
                items.add(new GuestPromotion(toPublic(promotion), canClaim, hasVoucher, reason));
                hasLoyaltyOffer = true;
            }
        }
        return new GuestPromotionListResponse(items,
                publicPage.total() + (hasLoyaltyOffer ? 1 : 0), p[0], p[1]);
    }

    private static boolean isWithinClaimWindow(PromotionRow promotion) {
        try {
            ensureWithinClaimWindow(promotion);
            return true;
        } catch (ApiError e) {
            return false;
        }
    }

    private PublicPromotion toPublic(PromotionRow row) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + PROMOTION_COLS + " FROM promotions p WHERE p.id = ?", row.id());
        return rows.isEmpty() ? null : publicPromotionFromRow(rows.get(0));
    }

    /**
     * {@code claim_guest_promotion}: a repeated claim is an idempotent read —
     * even after the campaign closes — because the unique promotion/guest
     * constraint makes retries safe without consuming another claim.
     */
    public Voucher claimGuestPromotion(GuestPromotionsTx tx, long guestId, long promotionId,
            String clientRequestId, String ipAddress, String userAgent) {
        requestIdIsValid(clientRequestId);
        Voucher existing = findVoucherByPromotionGuest(promotionId, guestId, true);
        if (existing != null) {
            return existing;
        }

        GuestPromotionsTx.ClaimPhase phase =
                tx.claimPhase(promotionId, guestId, ipAddress, userAgent);
        if (phase instanceof GuestPromotionsTx.ClaimPhase.LoyaltyPath) {
            GuestLoyalty.RewardRow reward =
                    loyalty.findActiveRewardByName(GuestLoyalty.JULY_DELUXE_LOYALTY_REWARD_NAME);
            if (reward == null) {
                throw ApiError.internal("July Deluxe loyalty reward is not configured.");
            }
            loyalty.redeemRewardForGuest(guestId, reward.id(), this);
            Voucher voucher = findVoucherByPromotionGuest(promotionId, guestId, true);
            if (voucher == null) {
                throw ApiError.internal("Redeemed loyalty voucher was not found.");
            }
            return voucher;
        }
        if (phase instanceof GuestPromotionsTx.ClaimPhase.Claimed claimed) {
            Voucher voucher = findVoucherForGuest(claimed.voucherId(), guestId);
            if (voucher == null) {
                throw ApiError.internal("Claimed voucher was not found");
            }
            return voucher;
        }
        Voucher voucher = findVoucherByPromotionGuest(promotionId, guestId, true);
        if (voucher == null) {
            throw ApiError.internal("Existing voucher was not found");
        }
        return voucher;
    }

    /** {@code list_guest_vouchers}. */
    public VoucherListResponse listGuestVouchers(long guestId, Long page, Long pageSize) {
        long[] p = pagination(page, pageSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM vouchers WHERE guest_id = ?", Long.class, guestId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + VOUCHER_COLS + " FROM vouchers v"
                        + " JOIN promotions p ON p.id = v.promotion_id"
                        + " WHERE v.guest_id = ?"
                        + " ORDER BY CASE v.status WHEN 'available' THEN 0 WHEN 'redeemed' THEN 1"
                        + "          ELSE 2 END,"
                        + "          v.expires_at NULLS LAST, v.created_at DESC"
                        + " LIMIT ? OFFSET ?",
                guestId, p[1], p[2]);
        List<Voucher> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(voucherFromRow(row, true));
        }
        return new VoucherListResponse(items, total, p[0], p[1]);
    }
}
