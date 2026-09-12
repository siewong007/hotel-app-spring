package com.hotelapp.promotions;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.promotions.GuestPromotions.PromotionRow;
import com.hotelapp.promotions.PromotionValidation.PromotionDraft;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped half of the admin promotion/voucher mutations —
 * isolated on its own bean so the {@code @Transactional} proxy actually
 * applies, matching upstream's {@code pool.begin()} bodies.
 */
@Component
public class AdminPromotionsTx {

    private final JdbcTemplate jdbc;
    private final GuestPromotions promotions;
    private final AuditWriter audit;

    public AdminPromotionsTx(JdbcTemplate jdbc, GuestPromotions promotions,
            AuditWriter audit) {
        this.jdbc = jdbc;
        this.promotions = promotions;
        this.audit = audit;
    }

    /** {@code ensure_admin_issueable}. */
    private void ensureAdminIssueable(PromotionRow promotion) {
        if (!"published".equals(promotion.status())) {
            throw ApiError.conflict("Only published promotions can issue vouchers");
        }
        GuestPromotions.ensureWithinClaimWindow(promotion);
    }

    /** {@code ensure_publishable}. */
    private void ensurePublishable(PromotionRow promotion) {
        if (!"draft".equals(promotion.status()) && !"paused".equals(promotion.status())) {
            throw ApiError.conflict(
                    "Only draft or paused promotions can be published");
        }
        if (promotion.claimLimit() != null
                && promotion.claimLimit() < promotion.claimedCount()) {
            throw ApiError.badRequest(
                    "Claim limit cannot be below the number of existing claims");
        }
    }

    /** {@code PromotionRepository::replace_room_type_targets}. */
    private void replaceRoomTypeTargets(long promotionId, List<Long> roomTypeIds) {
        jdbc.update("DELETE FROM promotion_room_types WHERE promotion_id = ?", promotionId);
        for (Long roomTypeId : roomTypeIds) {
            jdbc.update("INSERT INTO promotion_room_types (promotion_id, room_type_id)"
                    + " VALUES (?, ?)", promotionId, roomTypeId);
        }
    }

    /** {@code create_admin_promotion}'s tx body: insert + targets + audit. */
    @Transactional
    public long createPromotion(PromotionDraft draft, long actorId, String ipAddress,
            String userAgent) {
        Long promotionId = jdbc.queryForObject("""
                INSERT INTO promotions (
                    slug, name, description, terms, promotion_kind, discount_type,
                    discount_value, max_discount_amount, currency, claim_starts_at,
                    claim_ends_at, stay_starts_on, stay_ends_on, min_nights, max_nights,
                    min_subtotal, claim_limit, per_guest_limit, is_public, is_cancellable,
                    created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, draft.slug(), draft.name(), draft.description(),
                draft.terms(), draft.promotionKind(), draft.discountType(),
                draft.discountValue(), draft.maxDiscountAmount(), draft.currency(),
                draft.claimStartsAt(), draft.claimEndsAt(), draft.stayStartsOn(),
                draft.stayEndsOn(), draft.minNights(), draft.maxNights(),
                draft.minSubtotal(), draft.claimLimit(), draft.perGuestLimit(),
                draft.isPublic(), draft.isCancellable(), actorId, actorId);
        replaceRoomTypeTargets(promotionId, draft.roomTypeIds());
        audit.event(actorId, "promotion.created", "promotion", promotionId,
                Map.of("status", "draft", "promotion_kind", draft.promotionKind()),
                ipAddress, userAgent);
        return promotionId;
    }

    /**
     * {@code update_admin_promotion}'s tx body: only draft/paused rows edit;
     * the version guard makes concurrent saves conflict instead of clobber.
     */
    @Transactional
    public void updatePromotion(long promotionId, Long expectedVersion,
            PromotionDraft draft, long actorId, String ipAddress, String userAgent) {
        PromotionRow existing = promotions.findPromotionById(promotionId);
        if (existing == null) {
            throw ApiError.notFound("Promotion not found");
        }
        if (!"draft".equals(existing.status()) && !"paused".equals(existing.status())) {
            throw ApiError.conflict(
                    "Published or archived promotions cannot be edited."
                            + " Pause and create a new version instead.");
        }
        Long updated = jdbc.query("""
                UPDATE promotions SET
                    slug = ?, name = ?, description = ?, terms = ?,
                    promotion_kind = ?, discount_type = ?, discount_value = ?,
                    max_discount_amount = ?, currency = ?, claim_starts_at = ?,
                    claim_ends_at = ?, stay_starts_on = ?, stay_ends_on = ?,
                    min_nights = ?, max_nights = ?, min_subtotal = ?,
                    claim_limit = ?, per_guest_limit = ?, is_public = ?,
                    is_cancellable = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = ?
                  AND (?::bigint IS NULL OR version = ?)
                  AND status IN ('draft', 'paused')
                RETURNING id
                """, (rs, i) -> rs.getLong(1), draft.slug(), draft.name(),
                draft.description(), draft.terms(), draft.promotionKind(),
                draft.discountType(), draft.discountValue(), draft.maxDiscountAmount(),
                draft.currency(), draft.claimStartsAt(), draft.claimEndsAt(),
                draft.stayStartsOn(), draft.stayEndsOn(), draft.minNights(),
                draft.maxNights(), draft.minSubtotal(), draft.claimLimit(),
                draft.perGuestLimit(), draft.isPublic(), draft.isCancellable(),
                actorId, promotionId, expectedVersion, expectedVersion)
                .stream().findFirst().orElse(null);
        if (updated == null) {
            throw ApiError.conflict(
                    "This promotion changed. Refresh it before saving.");
        }
        replaceRoomTypeTargets(promotionId, draft.roomTypeIds());
        audit.event(actorId, "promotion.updated", "promotion", promotionId,
                Map.of("previous_version", existing.version()), ipAddress, userAgent);
    }

    /** {@code transition_admin_promotion}'s tx body + state machine. */
    @Transactional
    public void transitionPromotion(long promotionId, String nextStatus,
            Long expectedVersion, long actorId, String ipAddress, String userAgent) {
        PromotionRow current = promotions.findPromotionById(promotionId);
        if (current == null) {
            throw ApiError.notFound("Promotion not found");
        }
        switch (nextStatus) {
            case "published" -> ensurePublishable(current);
            case "paused" -> {
                if (!"published".equals(current.status())) {
                    throw ApiError.conflict(
                            "Only published promotions can be paused");
                }
            }
            case "archived" -> {
                if ("archived".equals(current.status())) {
                    throw ApiError.conflict("This promotion is already archived");
                }
            }
            default -> throw ApiError.badRequest("Unsupported promotion action");
        }
        Long updated = jdbc.query("""
                UPDATE promotions
                SET status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE id = ? AND (?::bigint IS NULL OR version = ?)
                RETURNING id
                """, (rs, i) -> rs.getLong(1), nextStatus, actorId, promotionId,
                expectedVersion, expectedVersion).stream().findFirst().orElse(null);
        if (updated == null) {
            throw ApiError.conflict(
                    "This promotion changed. Refresh it before continuing.");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previous_status", current.status());
        details.put("previous_version", current.version());
        audit.event(actorId, "promotion." + nextStatus, "promotion", promotionId,
                details, ipAddress, userAgent);
    }

    /** {@code issue_admin_voucher}'s tx body. */
    @Transactional
    public long issueVoucher(long promotionId, long guestId, String code,
            OffsetDateTime expiresAt, long actorId, String ipAddress, String userAgent) {
        PromotionRow promotion = promotions.findPromotionById(promotionId);
        if (promotion == null) {
            throw ApiError.notFound("Promotion not found");
        }
        ensureAdminIssueable(promotion);
        Long voucherId = promotions.insertVoucherIfNew(promotionId, guestId, code,
                "admin_issue", expiresAt, actorId);
        if (voucherId == null) {
            throw ApiError.conflict(
                    "This guest already has a voucher for this promotion");
        }
        if (!promotions.reserveClaimCapacity(promotionId)) {
            throw ApiError.conflict("This promotion has reached its claim limit");
        }
        audit.event(actorId, "voucher.issued", "voucher", voucherId,
                Map.of("promotion_id", promotionId, "guest_id", guestId,
                        "source", "admin_issue"),
                ipAddress, userAgent);
        return voucherId;
    }

    /** {@code revoke_admin_voucher}'s tx body. */
    @Transactional
    public void revokeVoucher(long voucherId, long promotionId, long guestId,
            long actorId, String reason, String ipAddress, String userAgent) {
        Long revoked = jdbc.query("""
                UPDATE vouchers
                SET status = 'revoked', revoked_at = CURRENT_TIMESTAMP, revoked_by = ?,
                    revocation_reason = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'available'
                RETURNING id
                """, (rs, i) -> rs.getLong(1), actorId, reason, voucherId)
                .stream().findFirst().orElse(null);
        if (revoked == null) {
            throw ApiError.conflict(
                    "This voucher changed. Refresh it before revoking.");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("promotion_id", promotionId);
        details.put("guest_id", guestId);
        details.put("reason", reason);
        audit.event(actorId, "voucher.revoked", "voucher", voucherId, details,
                ipAddress, userAgent);
    }
}
