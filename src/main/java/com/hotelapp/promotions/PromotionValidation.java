package com.hotelapp.promotions;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.promotions.PromotionModels.PromotionInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Port of {@code modules/promotions/validation.rs} — the promotion and
 * voucher input rules shared by the admin surface.
 */
public final class PromotionValidation {

    public static final List<String> PROMOTION_STATUSES =
            List.of("draft", "published", "paused", "archived");
    public static final List<String> PROMOTION_KINDS = List.of("deal", "voucher");
    public static final List<String> DISCOUNT_TYPES = List.of("percentage", "fixed_amount");
    public static final List<String> VOUCHER_STATUSES =
            List.of("available", "redeemed", "revoked");

    private PromotionValidation() {
    }

    /** {@code PromotionDraft}: sanitized, validated promotion write payload. */
    public record PromotionDraft(
            String slug,
            String name,
            String description,
            String terms,
            String promotionKind,
            String discountType,
            BigDecimal discountValue,
            BigDecimal maxDiscountAmount,
            String currency,
            OffsetDateTime claimStartsAt,
            OffsetDateTime claimEndsAt,
            LocalDate stayStartsOn,
            LocalDate stayEndsOn,
            Integer minNights,
            Integer maxNights,
            BigDecimal minSubtotal,
            Long claimLimit,
            int perGuestLimit,
            boolean isPublic,
            boolean isCancellable,
            List<Long> roomTypeIds) {
    }

    public static String normalizedChoice(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    public static String validateStatus(String value) {
        String normalized = normalizedChoice(value);
        if (!PROMOTION_STATUSES.contains(normalized)) {
            throw ApiError.badRequest("Unsupported promotion status");
        }
        return normalized;
    }

    public static String validateVoucherStatus(String value) {
        String normalized = normalizedChoice(value);
        if (!VOUCHER_STATUSES.contains(normalized)) {
            throw ApiError.badRequest("Unsupported voucher status");
        }
        return normalized;
    }

    private static String sanitizeRequiredText(String value, String field, int min, int max) {
        String cleaned = value == null ? "" : Sanitizer.sanitizeNotes(value).trim();
        int len = cleaned.codePointCount(0, cleaned.length());
        if (len < min || len > max) {
            throw ApiError.badRequest(
                    field + " must be between " + min + " and " + max + " characters");
        }
        return cleaned;
    }

    private static String sanitizeOptionalText(String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = Sanitizer.sanitizeNotes(value).trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.codePointCount(0, cleaned.length()) > max) {
            throw ApiError.badRequest(field + " cannot exceed " + max + " characters");
        }
        return cleaned;
    }

    public static String normalizeSlug(String value) {
        String slug = (value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        while (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        if (slug.length() < 3 || slug.length() > 80 || !slug.chars().allMatch(
                c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) {
            throw ApiError.badRequest(
                    "Promotion slug must use 3-80 lowercase letters, numbers, or hyphens");
        }
        return slug;
    }

    public static String normalizeVoucherCode(String value) {
        String code = (value == null ? "" : value).trim().toUpperCase(Locale.ROOT)
                .replace(" ", "").replace("-", "");
        if (code.length() < 8 || code.length() > 64
                || !code.chars().allMatch(Character::isLetterOrDigit)) {
            throw ApiError.badRequest("Voucher code must use 8-64 letters or numbers");
        }
        return code;
    }

    /**
     * {@code decimal_from_f64}: rejects non-finite values (JSON cannot carry
     * them, so a null means the required field was absent) and mirrors
     * upstream's string-round-trip parse.
     */
    static BigDecimal decimalFromF64(Double value, String field) {
        if (value == null || !Double.isFinite(value)) {
            throw ApiError.badRequest(field + " must be a finite number");
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            throw ApiError.badRequest("Invalid " + field);
        }
    }

    public static PromotionDraft validatePromotionInput(PromotionInput input) {
        String promotionKind = normalizedChoice(
                input.promotionKind() == null ? "" : input.promotionKind());
        if (!PROMOTION_KINDS.contains(promotionKind)) {
            throw ApiError.badRequest("Unsupported promotion kind");
        }
        String discountType = normalizedChoice(
                input.discountType() == null ? "" : input.discountType());
        if (!DISCOUNT_TYPES.contains(discountType)) {
            throw ApiError.badRequest("Unsupported discount type");
        }
        BigDecimal discountValue = decimalFromF64(input.discountValue(), "discount value");
        if (discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiError.badRequest("Discount value must be greater than zero");
        }
        if ("percentage".equals(discountType)
                && discountValue.compareTo(new BigDecimal("100")) > 0) {
            throw ApiError.badRequest("Percentage discounts cannot exceed 100");
        }
        BigDecimal maxDiscountAmount = input.maxDiscountAmount() == null
                ? null
                : decimalFromF64(input.maxDiscountAmount(), "maximum discount");
        if (maxDiscountAmount != null && maxDiscountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiError.badRequest("Maximum discount must be greater than zero");
        }
        if ("fixed_amount".equals(discountType) && maxDiscountAmount != null) {
            throw ApiError.badRequest(
                    "A maximum discount is only available for percentage promotions");
        }
        BigDecimal minSubtotal = input.minSubtotal() == null
                ? BigDecimal.ZERO
                : decimalFromF64(input.minSubtotal(), "minimum subtotal");
        if (minSubtotal.compareTo(BigDecimal.ZERO) < 0) {
            throw ApiError.badRequest("Minimum subtotal cannot be negative");
        }
        int minNights = input.minNights() == null ? 1 : input.minNights();
        if (minNights < 1 || (input.maxNights() != null && input.maxNights() < minNights)) {
            throw ApiError.badRequest("Invalid minimum or maximum stay length");
        }
        if (input.claimLimit() != null && input.claimLimit() < 0) {
            throw ApiError.badRequest("Claim limit cannot be negative");
        }
        int perGuestLimit = input.perGuestLimit() == null ? 1 : input.perGuestLimit();
        if (perGuestLimit != 1) {
            throw ApiError.badRequest(
                    "Per-guest limit is currently limited to one voucher per promotion");
        }
        if ((input.claimStartsAt() != null && input.claimEndsAt() != null
                && !input.claimStartsAt().isBefore(input.claimEndsAt()))
                || (input.stayStartsOn() != null && input.stayEndsOn() != null
                && input.stayStartsOn().isAfter(input.stayEndsOn()))) {
            throw ApiError.badRequest("Promotion date windows are invalid");
        }
        String currency = (input.currency() == null ? "USD" : input.currency())
                .trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3
                || !currency.chars().allMatch(c -> c >= 'A' && c <= 'Z')) {
            throw ApiError.badRequest("Currency must be a three-letter ISO code");
        }
        List<Long> roomTypeIds = input.roomTypeIds() == null
                ? List.of()
                : new ArrayList<>(input.roomTypeIds());
        if (roomTypeIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw ApiError.badRequest("Invalid room type target");
        }
        roomTypeIds = roomTypeIds.stream().sorted().distinct().toList();

        return new PromotionDraft(
                normalizeSlug(input.slug()),
                sanitizeRequiredText(input.name(), "Promotion name", 2, 160),
                sanitizeOptionalText(input.description(), "Promotion description", 2_000),
                sanitizeOptionalText(input.terms(), "Promotion terms", 4_000),
                promotionKind,
                discountType,
                discountValue,
                maxDiscountAmount,
                currency,
                input.claimStartsAt(),
                input.claimEndsAt(),
                input.stayStartsOn(),
                input.stayEndsOn(),
                minNights,
                input.maxNights(),
                minSubtotal,
                input.claimLimit(),
                perGuestLimit,
                Boolean.TRUE.equals(input.isPublic()),
                input.isCancellable() == null || input.isCancellable(),
                roomTypeIds);
    }

    public static String sanitizeOptionalReason(String value) {
        return sanitizeOptionalText(value, "Reason", 1_000);
    }
}
