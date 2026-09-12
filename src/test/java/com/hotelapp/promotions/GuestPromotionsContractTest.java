package com.hotelapp.promotions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.promotions.GuestPromotions.PromotionRow;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the pure promotion helpers mirrored from
 * modules/promotions/{service,repository}.rs.
 */
class GuestPromotionsContractTest {

    private static PromotionRow promotion(String slug, String status, boolean isPublic,
            OffsetDateTime startsAt, OffsetDateTime endsAt, Long claimLimit, long claimed) {
        return new PromotionRow(1, slug, status, isPublic, startsAt, endsAt,
                claimLimit, claimed);
    }

    // ---- mask_voucher_code (repository.rs) ------------------------------

    @Test
    void voucherCodeMaskKeepsLastFour() {
        assertEquals("••••ABCD", GuestPromotions.maskVoucherCode("VCH1234ABCD"));
        assertEquals("••••", GuestPromotions.maskVoucherCode(""));
        assertEquals("••••", GuestPromotions.maskVoucherCode(null));
        assertEquals("••••AB", GuestPromotions.maskVoucherCode("AB"));
    }

    // ---- generate_voucher_code (service.rs) -----------------------------

    @Test
    void voucherCodeIsVchPlus20Hex() {
        String code = GuestPromotions.generateVoucherCode();
        assertTrue(code.matches("VCH[0-9A-F]{20}"), code);
    }

    // ---- request_id_is_valid (service.rs) --------------------------------

    @Test
    void claimRequestIdsRejectBlankAndOverlong() {
        assertThrows(ApiError.class, () -> GuestPromotions.requestIdIsValid("  "));
        assertThrows(ApiError.class,
                () -> GuestPromotions.requestIdIsValid("x".repeat(129)));
        GuestPromotions.requestIdIsValid(null);
        GuestPromotions.requestIdIsValid("claim-1");
    }

    // ---- promotion_is_within_claim_window (service.rs) -------------------

    @Test
    void claimWindowRejectsFutureAndPast() {
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        OffsetDateTime past = OffsetDateTime.now().minusDays(1);
        assertThrows(ApiError.class, () -> GuestPromotions.ensureWithinClaimWindow(
                promotion("deal", "published", true, future, null, null, 0)));
        assertThrows(ApiError.class, () -> GuestPromotions.ensureWithinClaimWindow(
                promotion("deal", "published", true, null, past, null, 0)));
        assertThrows(ApiError.class, () -> GuestPromotions.ensureWithinClaimWindow(
                promotion("deal", "published", true, null, null, 5L, 5)));
        GuestPromotions.ensureWithinClaimWindow(
                promotion("deal", "published", true, past, future, 5L, 4));
        GuestPromotions.ensureWithinClaimWindow(
                promotion("deal", "published", true, null, null, null, 99));
    }

    // ---- ensure_guest_claimable (service.rs) -----------------------------

    @Test
    void guestClaimableRejectsLoyaltyAndUnpublished() {
        assertThrows(ApiError.class, () -> GuestPromotions.ensureGuestClaimable(
                promotion(GuestPromotions.JULY_DELUXE_LOYALTY_PROMOTION_SLUG,
                        "published", true, null, null, null, 0)));
        assertThrows(ApiError.class, () -> GuestPromotions.ensureGuestClaimable(
                promotion("deal", "draft", true, null, null, null, 0)));
        assertThrows(ApiError.class, () -> GuestPromotions.ensureGuestClaimable(
                promotion("deal", "published", false, null, null, null, 0)));
        GuestPromotions.ensureGuestClaimable(
                promotion("deal", "published", true, null, null, null, 0));
    }
}
