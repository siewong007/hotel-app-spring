package com.hotelapp.guests;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared guest-row shaping: the column list every guest-returning endpoint
 * selects (verbatim upstream aliases — `address_line1`, `state_province`),
 * the JSON view of that row, and the eKYC summary attached to it.
 */
public final class GuestViews {

    private GuestViews() {
    }

    /**
     * The upstream Guest select list, minus the portal-account and aggregate
     * fields only the paginated list computes.
     */
    public static final String COLUMNS = """
        id, nick_name, first_name, last_name, email, phone, ic_number, nationality,
        address_line_1 AS address_line1, city, state AS state_province,
        postal_code, country, title, alt_phone, true AS is_active,
        guest_type, tourism_type,
        COALESCE(discount_percentage, 0) AS discount_percentage, company_name,
        COALESCE(complimentary_nights_credit, 0) AS complimentary_nights_credit,
        created_at, updated_at,
        NULL::BIGINT AS bookings_count,
        NULL::DATE AS last_stay_date
        """;

    /** `COLUMNS` plus the linked-account and booking aggregates of the list endpoint. */
    public static final String LIST_COLUMNS = """
        id, nick_name, first_name, last_name, email, phone, ic_number, nationality,
        address_line_1 AS address_line1, city, state AS state_province,
        postal_code, country, title, alt_phone, true AS is_active,
        guest_type, tourism_type,
        COALESCE(discount_percentage, 0) AS discount_percentage, company_name,
        COALESCE(complimentary_nights_credit, 0) AS complimentary_nights_credit,
        created_at, updated_at,
        (SELECT username FROM users u
            WHERE u.guest_id = guests.id
              AND u.deleted_at IS NULL
            ORDER BY u.is_active DESC, u.id
            LIMIT 1) AS account_username,
        (SELECT is_active FROM users u
            WHERE u.guest_id = guests.id
              AND u.deleted_at IS NULL
            ORDER BY u.is_active DESC, u.id
            LIMIT 1) AS account_is_active,
        (SELECT COUNT(*) FROM bookings b
            WHERE b.guest_id = guests.id AND b.status != 'voided') AS bookings_count,
        (SELECT MAX(b.check_in_date) FROM bookings b
            WHERE b.guest_id = guests.id
              AND b.status IN ('checked_in', 'auto_checked_in', 'checked_out', 'completed')
        ) AS last_stay_date
        """;

    /** `COLUMNS` with `g.` prefixes, for the `user_guests` join in `linked_guests`. */
    public static final String LINKED_COLUMNS = """
        g.id, g.nick_name, g.first_name, g.last_name, g.email, g.phone, g.ic_number,
        g.nationality,
        g.address_line_1 AS address_line1, g.city, g.state AS state_province,
        g.postal_code, g.country, g.title, g.alt_phone, true AS is_active,
        g.guest_type, g.tourism_type,
        COALESCE(g.discount_percentage, 0) AS discount_percentage, g.company_name,
        COALESCE(g.complimentary_nights_credit, 0) AS complimentary_nights_credit,
        g.created_at, g.updated_at,
        NULL::BIGINT AS bookings_count,
        NULL::DATE AS last_stay_date
        """;

    private static final List<String> FIELD_ORDER = List.of(
            "id", "nick_name", "first_name", "last_name", "email", "phone",
            "ic_number", "nationality", "address_line1", "city", "state_province",
            "postal_code", "country", "title", "alt_phone", "is_active",
            "guest_type", "tourism_type", "discount_percentage", "company_name",
            "complimentary_nights_credit", "created_at", "updated_at");

    /**
     * Optional aggregate fields upstream `skip_serializing_if = Option::is_none` —
     * absent from the JSON when the query did not compute them.
     */
    private static final List<String> OPTIONAL_FIELDS = List.of(
            "account_username", "account_is_active", "bookings_count",
            "last_stay_date", "ekyc_summary");

    /**
     * The JSON view of one selected guest row: upstream field order, optional
     * fields omitted when null, `is_active` always a boolean.
     */
    public static Map<String, Object> json(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String field : FIELD_ORDER) {
            out.put(field, row.get(field));
        }
        for (String field : OPTIONAL_FIELDS) {
            Object value = row.get(field);
            if (value != null) {
                out.put(field, value);
            }
        }
        return out;
    }

    /** Latest-verification summary for one guest, `not_submitted` when none exists. */
    public static Map<String, Object> ekycSummary(JdbcTemplate jdbc, long guestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, guest_id, status,
                       COALESCE(self_checkin_enabled, false) AS self_checkin_enabled,
                       verified_at
                FROM ekyc_verifications
                WHERE guest_id = ?
                ORDER BY COALESCE(submitted_at, created_at) DESC, updated_at DESC, id DESC
                LIMIT 1
                """, guestId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("guest_id", guestId);
        if (rows.isEmpty()) {
            summary.put("ekyc_verification_id", null);
            summary.put("status", "not_submitted");
            summary.put("self_checkin_enabled", false);
            summary.put("verified_at", null);
            summary.put("can_auto_checkin", false);
            summary.put("auto_checkin_block_reason", "eKYC has not been submitted.");
            return summary;
        }
        Map<String, Object> record = rows.get(0);
        String status = normalizeEkycStatus((String) record.get("status"));
        boolean selfCheckin = Boolean.TRUE.equals(record.get("self_checkin_enabled"));
        boolean approved = status.equals("approved");
        boolean canAutoCheckin = approved && selfCheckin;
        summary.put("ekyc_verification_id", record.get("id"));
        summary.put("status", status);
        summary.put("self_checkin_enabled", selfCheckin);
        summary.put("verified_at", record.get("verified_at"));
        summary.put("can_auto_checkin", canAutoCheckin);
        String blockReason = null;
        if (!canAutoCheckin) {
            blockReason = approved
                    ? "Self check-in is not enabled for this eKYC verification."
                    : ekycStatusBlockReason(status);
        }
        summary.put("auto_checkin_block_reason", blockReason);
        return summary;
    }

    /** Adds `ekyc_summary` to one shaped guest row. */
    public static void attachEkycSummary(JdbcTemplate jdbc, Map<String, Object> guest) {
        guest.put("ekyc_summary", ekycSummary(jdbc, ((Number) guest.get("id")).longValue()));
    }

    /** Adds `ekyc_summary` to every shaped guest row, upstream's batch attach. */
    public static void attachEkycSummaries(JdbcTemplate jdbc, List<Map<String, Object>> guests) {
        for (Map<String, Object> guest : guests) {
            attachEkycSummary(jdbc, guest);
        }
    }

    /** Upstream auto_checkin::normalize_ekyc_status, verbatim. */
    public static String normalizeEkycStatus(String status) {
        return switch (status == null ? "" : status) {
            case "approved", "verified" -> "approved";
            case "rejected" -> "rejected";
            case "expired" -> "expired";
            case "void", "cancelled", "canceled" -> "void";
            case "pending_manual_review", "in_review", "under_review", "on_hold",
                    "additional_information_required", "escalated" -> "in_review";
            default -> "pending";
        };
    }

    private static String ekycStatusBlockReason(String status) {
        return switch (status) {
            case "pending" -> "eKYC is pending approval.";
            case "in_review" -> "eKYC is still in review.";
            case "rejected" -> "eKYC was rejected.";
            case "expired" -> "eKYC has expired.";
            case "void" -> "eKYC has been voided.";
            default -> "Approved eKYC is required for auto check-in.";
        };
    }

    /** Upstream display_name: legal name when both halves exist, else nick_name. */
    public static String displayName(String nickName, String firstName, String lastName) {
        String first = firstName == null || firstName.isBlank() ? null : firstName.trim();
        String last = lastName == null || lastName.isBlank() ? null : lastName.trim();
        if (first != null && last != null) {
            return first + " " + last;
        }
        return nickName.trim();
    }
}
