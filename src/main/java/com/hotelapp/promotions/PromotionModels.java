package com.hotelapp.promotions;

import com.hotelapp.core.json.DenyUnknownFields;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs mirroring {@code modules/promotions/models.rs} — the guest-facing
 * promotion and voucher shapes. Field names are snake_case byte-identical.
 */
public final class PromotionModels {

    private PromotionModels() {
    }

    /**
     * {@code Promotion}: the staff-facing shape — {@code PublicPromotion}
     * plus the {@code created_by}/{@code updated_by} actor columns.
     */
    public record Promotion(
            long id,
            String slug,
            String name,
            String description,
            String terms,
            String status,
            @JsonProperty("promotion_kind") String promotionKind,
            @JsonProperty("discount_type") String discountType,
            @JsonProperty("discount_value") BigDecimal discountValue,
            @JsonProperty("max_discount_amount") BigDecimal maxDiscountAmount,
            String currency,
            @JsonProperty("claim_starts_at") OffsetDateTime claimStartsAt,
            @JsonProperty("claim_ends_at") OffsetDateTime claimEndsAt,
            @JsonProperty("stay_starts_on") LocalDate stayStartsOn,
            @JsonProperty("stay_ends_on") LocalDate stayEndsOn,
            @JsonProperty("min_nights") Integer minNights,
            @JsonProperty("max_nights") Integer maxNights,
            @JsonProperty("min_subtotal") BigDecimal minSubtotal,
            @JsonProperty("claim_limit") Long claimLimit,
            @JsonProperty("claimed_count") long claimedCount,
            @JsonProperty("per_guest_limit") int perGuestLimit,
            @JsonProperty("is_public") boolean isPublic,
            @JsonProperty("is_cancellable") boolean isCancellable,
            @JsonProperty("room_type_ids") List<Long> roomTypeIds,
            long version,
            @JsonProperty("created_by") Long createdBy,
            @JsonProperty("updated_by") Long updatedBy,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt) {
    }

    /**
     * {@code PublicPromotion}: promotion fields safe to expose to guests and
     * public catalogue visitors — staff actor ids stay off this shape.
     */
    public record PublicPromotion(
            long id,
            String slug,
            String name,
            String description,
            String terms,
            String status,
            @JsonProperty("promotion_kind") String promotionKind,
            @JsonProperty("discount_type") String discountType,
            @JsonProperty("discount_value") BigDecimal discountValue,
            @JsonProperty("max_discount_amount") BigDecimal maxDiscountAmount,
            String currency,
            @JsonProperty("claim_starts_at") OffsetDateTime claimStartsAt,
            @JsonProperty("claim_ends_at") OffsetDateTime claimEndsAt,
            @JsonProperty("stay_starts_on") LocalDate stayStartsOn,
            @JsonProperty("stay_ends_on") LocalDate stayEndsOn,
            @JsonProperty("min_nights") Integer minNights,
            @JsonProperty("max_nights") Integer maxNights,
            @JsonProperty("min_subtotal") BigDecimal minSubtotal,
            @JsonProperty("claim_limit") Long claimLimit,
            @JsonProperty("claimed_count") long claimedCount,
            @JsonProperty("per_guest_limit") int perGuestLimit,
            @JsonProperty("is_public") boolean isPublic,
            @JsonProperty("is_cancellable") boolean isCancellable,
            @JsonProperty("room_type_ids") List<Long> roomTypeIds,
            long version,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt) {
    }

    public record GuestPromotion(
            PublicPromotion promotion,
            @JsonProperty("can_claim") boolean canClaim,
            @JsonProperty("has_voucher") boolean hasVoucher,
            @JsonProperty("claim_unavailable_reason")
            @JsonInclude(JsonInclude.Include.NON_NULL) String claimUnavailableReason) {
    }

    public record GuestPromotionListResponse(
            List<GuestPromotion> items, long total, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    public record PromotionListResponse(
            List<Promotion> items, long total, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    public record PublicPromotionListResponse(
            List<PublicPromotion> items, long total, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    /**
     * {@code Voucher}: the owning portal guest sees the full {@code code};
     * staff lists get {@code null} there and display {@code code_masked}.
     */
    public record Voucher(
            long id,
            @JsonProperty("promotion_id") long promotionId,
            @JsonProperty("guest_id") long guestId,
            @JsonProperty("promotion_name") String promotionName,
            @JsonProperty("promotion_slug") String promotionSlug,
            String code,
            @JsonProperty("code_masked") String codeMasked,
            String status,
            String source,
            @JsonProperty("expires_at") OffsetDateTime expiresAt,
            @JsonProperty("claimed_at") OffsetDateTime claimedAt,
            @JsonProperty("redeemed_at") OffsetDateTime redeemedAt,
            @JsonProperty("revoked_at") OffsetDateTime revokedAt,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    public record VoucherListResponse(
            List<Voucher> items, long total, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    /** {@code ClaimPromotionInput} — {@code deny_unknown_fields} upstream. */
    @DenyUnknownFields
    public record ClaimPromotionInput(
            @JsonProperty("client_request_id") String clientRequestId) {
    }

    /**
     * {@code PromotionInput}: required scalar fields stay boxed so a missing
     * body field is rejected by the service instead of NPE-ing (upstream
     * deserialization would 422 on a missing {@code String}).
     */
    public record PromotionInput(
            String slug,
            String name,
            String description,
            String terms,
            @JsonProperty("promotion_kind") String promotionKind,
            @JsonProperty("discount_type") String discountType,
            @JsonProperty("discount_value") Double discountValue,
            @JsonProperty("max_discount_amount") Double maxDiscountAmount,
            String currency,
            @JsonProperty("claim_starts_at") OffsetDateTime claimStartsAt,
            @JsonProperty("claim_ends_at") OffsetDateTime claimEndsAt,
            @JsonProperty("stay_starts_on") LocalDate stayStartsOn,
            @JsonProperty("stay_ends_on") LocalDate stayEndsOn,
            @JsonProperty("min_nights") Integer minNights,
            @JsonProperty("max_nights") Integer maxNights,
            @JsonProperty("min_subtotal") Double minSubtotal,
            @JsonProperty("claim_limit") Long claimLimit,
            @JsonProperty("per_guest_limit") Integer perGuestLimit,
            @JsonProperty("is_public") Boolean isPublic,
            @JsonProperty("is_cancellable") Boolean isCancellable,
            @JsonProperty("room_type_ids") List<Long> roomTypeIds,
            @JsonProperty("expected_version") Long expectedVersion) {
    }

    public record PromotionActionInput(
            @JsonProperty("expected_version") Long expectedVersion) {
    }

    /** {@code VoucherIssueInput} — {@code deny_unknown_fields} upstream. */
    @DenyUnknownFields
    public record VoucherIssueInput(
            @JsonProperty("promotion_id") Long promotionId,
            @JsonProperty("guest_id") Long guestId,
            String code,
            @JsonProperty("expires_at") OffsetDateTime expiresAt) {
    }

    /** {@code VoucherRevokeInput} — {@code deny_unknown_fields} upstream. */
    @DenyUnknownFields
    public record VoucherRevokeInput(String reason) {
    }
}
