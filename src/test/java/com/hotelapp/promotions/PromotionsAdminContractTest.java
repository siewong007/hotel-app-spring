package com.hotelapp.promotions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.ObjectMapper;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.promotions.PromotionModels.PromotionInput;
import com.hotelapp.promotions.PromotionModels.VoucherIssueInput;
import com.hotelapp.promotions.PromotionModels.VoucherRevokeInput;
import com.hotelapp.promotions.PromotionValidation.PromotionDraft;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link PromotionValidation} and the promotions request
 * DTOs — mirroring the cases in {@code modules/promotions/validation.rs} and
 * {@code models.rs} tests.
 */
class PromotionsAdminContractTest {

    private static PromotionInput input(String discountType, Double discountValue) {
        return new PromotionInput("welcome-deal", "Welcome deal", null, null, "voucher",
                discountType, discountValue, null, "USD", null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void normalizesVoucherCodesWithoutPreservingSeparators() {
        assertEquals("VCHAB1234CD", PromotionValidation.normalizeVoucherCode(" vch-ab12 34cd "));
    }

    @Test
    void rejectsPercentageAboveOneHundred() {
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(
                        input("percentage", 100.01)));
    }

    @Test
    void rejectsPerGuestLimitsTheVoucherConstraintCannotHonor() {
        PromotionInput request = new PromotionInput("welcome-deal", "Welcome deal", null,
                null, "voucher", "percentage", 10.0, null, "USD", null, null, null, null,
                null, null, null, null, 2, null, null, null, null);
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(request));
    }

    @Test
    void defaultsAnOmittedMinimumStayToOneNight() {
        PromotionDraft draft = PromotionValidation.validatePromotionInput(
                input("percentage", 10.0));
        assertEquals(1, draft.minNights());
        assertEquals(BigDecimal.ZERO, draft.minSubtotal());
    }

    @Test
    void normalizesSlugAndStripsEdgeHyphens() {
        assertEquals("welcome-deal", PromotionValidation.normalizeSlug(" -Welcome-Deal- "));
        assertThrows(ApiError.class, () -> PromotionValidation.normalizeSlug("ab"));
        assertThrows(ApiError.class,
                () -> PromotionValidation.normalizeSlug("bad slug!"));
    }

    @Test
    void statusChoicesNormalizeSeparators() {
        assertEquals("published", PromotionValidation.validateStatus(" PUBLISHED "));
        assertEquals("fixed_amount",
                PromotionValidation.normalizedChoice("Fixed-Amount"));
        assertThrows(ApiError.class, () -> PromotionValidation.validateStatus("live"));
        assertThrows(ApiError.class,
                () -> PromotionValidation.validateVoucherStatus("expired"));
    }

    @Test
    void maxDiscountOnlyAppliesToPercentage() {
        PromotionInput request = new PromotionInput("fixed-deal", "Fixed deal", null, null,
                "deal", "fixed_amount", 10.0, 5.0, "USD", null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(request));
    }

    @Test
    void roomTypeTargetsSortDedupAndRejectNonPositive() {
        PromotionInput bad = new PromotionInput("room-deal", "Room deal", null, null,
                "deal", "percentage", 10.0, null, "USD", null, null, null, null, null,
                null, null, null, null, null, null, List.of(3L, 0L), null);
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(bad));

        PromotionInput good = new PromotionInput("room-deal", "Room deal", null, null,
                "deal", "percentage", 10.0, null, "USD", null, null, null, null, null,
                null, null, null, null, null, null, List.of(5L, 2L, 5L), null);
        assertEquals(List.of(2L, 5L),
                PromotionValidation.validatePromotionInput(good).roomTypeIds());
    }

    @Test
    void voucherInputsRejectMassAssignmentFields() {
        // serde deny_unknown_fields: marked DTOs reject extra fields while the
        // serde default (tolerate) stays on every other DTO.
        ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                .addHandler(com.hotelapp.core.json.DenyUnknownFieldsCustomizer.HANDLER)
                .build();
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"client_request_id\":\"claim-1\",\"guest_id\":99}",
                com.hotelapp.promotions.PromotionModels.ClaimPromotionInput.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"promotion_id\":1,\"guest_id\":2,\"status\":\"redeemed\"}",
                VoucherIssueInput.class));
        assertThrows(Exception.class, () -> mapper.readValue(
                "{\"reason\":\"test\",\"revoked_by\":99}", VoucherRevokeInput.class));

        // unmarked DTOs keep serde's default — unknown fields are tolerated
        record Plain(String name) {
        }
        try {
            mapper.readValue("{\"name\":\"a\",\"extra\":1}", Plain.class);
        } catch (Exception e) {
            throw new AssertionError("unmarked DTO rejected an unknown field", e);
        }
    }

    @Test
    void sanitizeOptionalReasonTrimsAndCaps() {
        assertNull(PromotionValidation.sanitizeOptionalReason("   "));
        assertEquals("duplicate",
                PromotionValidation.sanitizeOptionalReason("  duplicate  "));
        StringBuilder longReason = new StringBuilder("x".repeat(1001));
        assertThrows(ApiError.class,
                () -> PromotionValidation.sanitizeOptionalReason(longReason.toString()));
    }

    @Test
    void discountValueMustBeFiniteAndPositive() {
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(
                        input("percentage", 0.0)));
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(
                        input("percentage", -5.0)));
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(
                        input("percentage", null)));
        assertThrows(ApiError.class,
                () -> PromotionValidation.validatePromotionInput(
                        input("percentage", Double.NaN)));
    }
}
