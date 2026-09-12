package com.hotelapp.promotions;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.promotions.GuestPromotions.PromotionRow;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped half of {@code claim_guest_promotion} — isolated on its
 * own bean so the {@code @Transactional} proxy actually applies.
 */
@Component
public class GuestPromotionsTx {

    /** Result of the in-transaction phase of a promotion claim. */
    public sealed interface ClaimPhase {
        /** The promotion is the July Deluxe loyalty offer — redeem outside tx. */
        record LoyaltyPath() implements ClaimPhase {
        }

        /** A voucher row now exists; re-read it post-commit like upstream. */
        record Claimed(long voucherId) implements ClaimPhase {
        }

        /** insert hit the (promotion, guest) uniqueness — re-read the existing row. */
        record AlreadyClaimed() implements ClaimPhase {
        }
    }

    private final GuestPromotions promotions;
    private final AuditWriter audit;

    public GuestPromotionsTx(GuestPromotions promotions, AuditWriter audit) {
        this.promotions = promotions;
        this.audit = audit;
    }

    /**
     * {@code claim_guest_promotion}'s transaction body: lock the promotion row
     * read, verify claimability, insert the voucher, reserve capacity, audit.
     */
    @Transactional
    public ClaimPhase claimPhase(long promotionId, long guestId, String ipAddress,
            String userAgent) {
        PromotionRow promotion = promotions.findPromotionById(promotionId);
        if (promotion == null) {
            throw ApiError.notFound("Promotion not found");
        }
        if (GuestPromotions.JULY_DELUXE_LOYALTY_PROMOTION_SLUG.equals(promotion.slug())) {
            return new ClaimPhase.LoyaltyPath();
        }
        GuestPromotions.ensureGuestClaimable(promotion);

        Long voucherId = promotions.insertVoucherIfNew(promotionId, guestId,
                GuestPromotions.generateVoucherCode(), "guest_claim", null, null);
        if (voucherId == null) {
            return new ClaimPhase.AlreadyClaimed();
        }
        if (!promotions.reserveClaimCapacity(promotionId)) {
            throw ApiError.conflict("This promotion has reached its claim limit");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("promotion_id", promotionId);
        details.put("guest_id", guestId);
        details.put("source", "guest_portal");
        audit.event(null, "promotion.claimed", "voucher", voucherId, details,
                ipAddress, userAgent);
        return new ClaimPhase.Claimed(voucherId);
    }
}
