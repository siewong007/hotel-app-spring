package com.hotelapp.promotions;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.promotions.PromotionModels.Promotion;
import com.hotelapp.promotions.PromotionModels.PromotionInput;
import com.hotelapp.promotions.PromotionModels.PromotionListResponse;
import com.hotelapp.promotions.PromotionModels.Voucher;
import com.hotelapp.promotions.PromotionModels.VoucherIssueInput;
import com.hotelapp.promotions.PromotionModels.VoucherListResponse;
import com.hotelapp.promotions.PromotionModels.VoucherRevokeInput;
import com.hotelapp.promotions.PromotionValidation.PromotionDraft;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of the staff slice of {@code modules/promotions/service.rs} +
 * {@code repository.rs}: admin promotion CRUD/lifecycle and voucher
 * issue/list/revoke. Shares the claim-window helpers on
 * {@link GuestPromotions}.
 */
@Component
public class AdminPromotions {

    /**
     * {@code PROMOTION_COLUMNS} — the full staff projection including the
     * {@code created_by}/{@code updated_by} actor ids the public projection
     * deliberately omits.
     */
    private static final String PROMOTION_COLS = """
            p.id, p.slug, p.name, p.description, p.terms, p.status, p.promotion_kind,
            p.discount_type, p.discount_value, p.max_discount_amount, p.currency,
            p.claim_starts_at, p.claim_ends_at, p.stay_starts_on, p.stay_ends_on,
            p.min_nights, p.max_nights, p.min_subtotal, p.claim_limit, p.claimed_count,
            p.per_guest_limit, p.is_public, p.is_cancellable,
            CAST(p.version AS BIGINT) AS version, p.created_by, p.updated_by,
            p.created_at, p.updated_at
            """;

    private static final String VOUCHER_COLS = """
            v.id, v.promotion_id, v.guest_id, p.name AS promotion_name,
            p.slug AS promotion_slug, v.code, v.status, v.source, v.expires_at,
            v.claimed_at, v.redeemed_at, v.revoked_at, v.created_at
            """;

    private final JdbcTemplate jdbc;
    private final GuestPromotions guestPromotions;

    public AdminPromotions(JdbcTemplate jdbc, GuestPromotions guestPromotions) {
        this.jdbc = jdbc;
        this.guestPromotions = guestPromotions;
    }

    private static long[] pagination(Long page, Long pageSize) {
        long p = page == null ? 1 : Math.max(page, 1);
        long size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        return new long[] {p, size, (p - 1) * size};
    }

    private static String normalizedFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // ------------------------------------------------------------------
    // Row mapping
    // ------------------------------------------------------------------

    private Promotion promotionFromRow(Map<String, Object> row) {
        long promotionId = ((Number) row.get("id")).longValue();
        List<Long> roomTypeIds = jdbc.query(
                "SELECT room_type_id FROM promotion_room_types"
                        + " WHERE promotion_id = ? ORDER BY room_type_id",
                (rs, i) -> rs.getLong(1), promotionId);
        return new Promotion(
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
                row.get("created_by") instanceof Number n ? n.longValue() : null,
                row.get("updated_by") instanceof Number n ? n.longValue() : null,
                (OffsetDateTime) row.get("created_at"),
                (OffsetDateTime) row.get("updated_at"));
    }

    private List<Promotion> promotionsFromRows(List<Map<String, Object>> rows) {
        List<Promotion> promotions = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            promotions.add(promotionFromRow(row));
        }
        return promotions;
    }

    private Voucher voucherFromRow(Map<String, Object> row, boolean includeCode) {
        String rawCode = (String) row.get("code");
        return new Voucher(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("promotion_id")).longValue(),
                ((Number) row.get("guest_id")).longValue(),
                (String) row.get("promotion_name"),
                (String) row.get("promotion_slug"),
                includeCode ? rawCode : null,
                GuestPromotions.maskVoucherCode(rawCode),
                (String) row.get("status"),
                (String) row.get("source"),
                (OffsetDateTime) row.get("expires_at"),
                (OffsetDateTime) row.get("claimed_at"),
                (OffsetDateTime) row.get("redeemed_at"),
                (OffsetDateTime) row.get("revoked_at"),
                (OffsetDateTime) row.get("created_at"));
    }

    // ------------------------------------------------------------------
    // Repository: promotions
    // ------------------------------------------------------------------

    /** {@code PromotionRepository::find_by_id} — staff projection. */
    public Promotion findPromotion(long promotionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + PROMOTION_COLS + " FROM promotions p WHERE p.id = ?",
                promotionId);
        return rows.isEmpty() ? null : promotionFromRow(rows.get(0));
    }

    /** {@code updated_promotion} — post-commit re-read with 404. */
    public Promotion updatedPromotion(long promotionId) {
        Promotion promotion = findPromotion(promotionId);
        if (promotion == null) {
            throw ApiError.notFound("Promotion not found");
        }
        return promotion;
    }

    // ------------------------------------------------------------------
    // Service: admin promotions
    // ------------------------------------------------------------------

    /** {@code list_admin_promotions}. */
    public PromotionListResponse listAdminPromotions(String status, String search,
            Long page, Long pageSize) {
        long[] p = pagination(page, pageSize);
        String normalizedStatus = normalizedFilter(status);
        if (normalizedStatus != null) {
            normalizedStatus = PromotionValidation.validateStatus(normalizedStatus);
        }
        String normalizedSearch = normalizedFilter(search);
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM promotions p
                WHERE (?::text IS NULL OR p.status = ?)
                  AND (?::text IS NULL OR LOWER(p.name) LIKE '%' || LOWER(?) || '%'
                       OR LOWER(p.slug) LIKE '%' || LOWER(?) || '%')
                """, Long.class, normalizedStatus, normalizedStatus,
                normalizedSearch, normalizedSearch, normalizedSearch);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT %s FROM promotions p
                WHERE (?::text IS NULL OR p.status = ?)
                  AND (?::text IS NULL OR LOWER(p.name) LIKE '%%' || LOWER(?) || '%%'
                       OR LOWER(p.slug) LIKE '%%' || LOWER(?) || '%%')
                ORDER BY p.updated_at DESC
                LIMIT ? OFFSET ?
                """.formatted(PROMOTION_COLS), normalizedStatus, normalizedStatus,
                normalizedSearch, normalizedSearch, normalizedSearch, p[1], p[2]);
        return new PromotionListResponse(promotionsFromRows(rows), total, p[0], p[1]);
    }

    /** {@code create_admin_promotion}: validate → tx → re-read. */
    public Promotion createAdminPromotion(AdminPromotionsTx tx, long actorId,
            PromotionInput input, String ipAddress, String userAgent) {
        PromotionDraft draft = PromotionValidation.validatePromotionInput(input);
        long promotionId = tx.createPromotion(draft, actorId, ipAddress, userAgent);
        return updatedPromotion(promotionId);
    }

    /** {@code update_admin_promotion}: validate → tx → re-read. */
    public Promotion updateAdminPromotion(AdminPromotionsTx tx, long actorId,
            long promotionId, PromotionInput input, String ipAddress, String userAgent) {
        Long expectedVersion = input.expectedVersion();
        PromotionDraft draft = PromotionValidation.validatePromotionInput(input);
        tx.updatePromotion(promotionId, expectedVersion, draft, actorId, ipAddress, userAgent);
        return updatedPromotion(promotionId);
    }

    /** {@code publish/pause/archive_admin_promotion} — shared state machine. */
    public Promotion transitionAdminPromotion(AdminPromotionsTx tx, long actorId,
            long promotionId, String nextStatus, Long expectedVersion,
            String ipAddress, String userAgent) {
        tx.transitionPromotion(promotionId, nextStatus, expectedVersion, actorId,
                ipAddress, userAgent);
        return updatedPromotion(promotionId);
    }

    // ------------------------------------------------------------------
    // Service: admin vouchers
    // ------------------------------------------------------------------

    /** {@code list_admin_vouchers}: staff lists mask voucher codes. */
    public VoucherListResponse listAdminVouchers(String status, String search,
            Long page, Long pageSize) {
        long[] p = pagination(page, pageSize);
        String normalizedStatus = normalizedFilter(status);
        if (normalizedStatus != null) {
            normalizedStatus = PromotionValidation.validateVoucherStatus(normalizedStatus);
        }
        String normalizedSearch = normalizedFilter(search);
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM vouchers v
                JOIN promotions p ON p.id = v.promotion_id
                WHERE (?::text IS NULL OR v.status = ?)
                  AND (?::text IS NULL OR LOWER(p.name) LIKE '%' || LOWER(?) || '%'
                       OR LOWER(v.code) = LOWER(?))
                """, Long.class, normalizedStatus, normalizedStatus,
                normalizedSearch, normalizedSearch, normalizedSearch);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT %s FROM vouchers v JOIN promotions p ON p.id = v.promotion_id
                WHERE (?::text IS NULL OR v.status = ?)
                  AND (?::text IS NULL OR LOWER(p.name) LIKE '%%' || LOWER(?) || '%%'
                       OR LOWER(v.code) = LOWER(?))
                ORDER BY v.created_at DESC LIMIT ? OFFSET ?
                """.formatted(VOUCHER_COLS), normalizedStatus, normalizedStatus,
                normalizedSearch, normalizedSearch, normalizedSearch, p[1], p[2]);
        List<Voucher> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(voucherFromRow(row, false));
        }
        return new VoucherListResponse(items, total, p[0], p[1]);
    }

    /** {@code find_voucher_admin} — staff reads never expose the raw code. */
    public Voucher findVoucherAdmin(long voucherId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + VOUCHER_COLS + " FROM vouchers v"
                        + " JOIN promotions p ON p.id = v.promotion_id WHERE v.id = ?",
                voucherId);
        return rows.isEmpty() ? null : voucherFromRow(rows.get(0), false);
    }

    /** {@code issue_admin_voucher}: pre-tx guards, then the tx bundle. */
    public Voucher issueAdminVoucher(AdminPromotionsTx tx, long actorId,
            VoucherIssueInput input, String ipAddress, String userAgent) {
        if (input.guestId() == null || input.promotionId() == null
                || input.guestId() <= 0 || input.promotionId() <= 0) {
            throw ApiError.badRequest("Invalid guest or promotion");
        }
        if (input.expiresAt() != null && !input.expiresAt().isAfter(OffsetDateTime.now())) {
            throw ApiError.badRequest("Voucher expiry must be in the future");
        }
        String code = input.code() == null
                ? GuestPromotions.generateVoucherCode()
                : PromotionValidation.normalizeVoucherCode(input.code());
        long voucherId = tx.issueVoucher(input.promotionId(), input.guestId(), code,
                input.expiresAt(), actorId, ipAddress, userAgent);
        Voucher voucher = findVoucherAdmin(voucherId);
        if (voucher == null) {
            throw ApiError.internal("Issued voucher was not found");
        }
        return voucher;
    }

    /** {@code revoke_admin_voucher}: read check, then the tx bundle. */
    public Voucher revokeAdminVoucher(AdminPromotionsTx tx, long actorId, long voucherId,
            VoucherRevokeInput input, String ipAddress, String userAgent) {
        String reason = PromotionValidation.sanitizeOptionalReason(
                input == null ? null : input.reason());
        Voucher existing = findVoucherAdmin(voucherId);
        if (existing == null) {
            throw ApiError.notFound("Voucher not found");
        }
        if (!"available".equals(existing.status())) {
            throw ApiError.conflict("Only available vouchers can be revoked");
        }
        tx.revokeVoucher(voucherId, existing.promotionId(), existing.guestId(), actorId,
                reason, ipAddress, userAgent);
        Voucher voucher = findVoucherAdmin(voucherId);
        if (voucher == null) {
            throw ApiError.internal("Revoked voucher was not found");
        }
        return voucher;
    }

    /** Kept for tx delegates that need the shared guest-side helpers. */
    GuestPromotions guestPromotions() {
        return guestPromotions;
    }
}
