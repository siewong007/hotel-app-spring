package com.hotelapp.portal;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.portal.PortalModels.AutoCheckinResponse;
import com.hotelapp.portal.PortalModels.GuestEkycStatusSummary;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of {@code services/auto_checkin.rs}: eKYC status normalization, the
 * eligibility summary, and the guest-portal auto check-in flow that rides on
 * the shared check-in transaction ({@code checkin_booking_flow}).
 */
@Component
public class AutoCheckin {

    public static final String AUTO_CHECKIN_SOURCE = "ekyc_auto_checkin";

    private final JdbcTemplate jdbc;
    private final PortalBookingOps bookings;
    private final PortalPaymentTx paymentTx;

    public AutoCheckin(JdbcTemplate jdbc, PortalBookingOps bookings, PortalPaymentTx paymentTx) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.paymentTx = paymentTx;
    }

    /** {@code normalize_ekyc_status}. */
    public static String normalizeEkycStatus(String status) {
        return switch (status) {
            case "approved", "verified" -> "approved";
            case "rejected" -> "rejected";
            case "expired" -> "expired";
            case "void", "cancelled", "canceled" -> "void";
            case "pending_manual_review", "in_review", "under_review", "on_hold",
                    "additional_information_required", "escalated" -> "in_review";
            case "draft", "submitted", "automated_review", "pending" -> "pending";
            default -> "pending";
        };
    }

    /** One eKYC summary record — {@code GuestEkycSummaryRecord}. */
    public record EkycSummaryRecord(
            long verificationId, long userId, long guestId, String status,
            boolean selfCheckinEnabled, Object verifiedAt) {
    }

    /** {@code EkycRepository::latest_guest_summary_record}. */
    public EkycSummaryRecord latestGuestSummaryRecord(long guestId) {
        List<EkycSummaryRecord> rows = jdbc.query("""
                SELECT id, user_id, guest_id, status,
                       COALESCE(self_checkin_enabled, false) AS self_checkin_enabled,
                       verified_at
                FROM ekyc_verifications
                WHERE guest_id = ?
                ORDER BY COALESCE(submitted_at, created_at) DESC, updated_at DESC, id DESC
                LIMIT 1
                """, (rs, i) -> new EkycSummaryRecord(
                rs.getLong("id"), rs.getLong("user_id"), rs.getLong("guest_id"),
                rs.getString("status"), rs.getBoolean("self_checkin_enabled"),
                rs.getObject("verified_at")), guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code summary_from_record}. */
    private GuestEkycStatusSummary summaryFromRecord(EkycSummaryRecord record) {
        String status = normalizeEkycStatus(record.status());
        boolean approved = "approved".equals(status);
        boolean canAutoCheckin = approved && record.selfCheckinEnabled();
        String blockReason;
        if (canAutoCheckin) {
            blockReason = null;
        } else if (!approved) {
            blockReason = ekycStatusBlockReason(status);
        } else {
            blockReason = "Self check-in is not enabled for this eKYC verification.";
        }
        return new GuestEkycStatusSummary(record.guestId(), record.verificationId(), status,
                record.selfCheckinEnabled(), record.verifiedAt(), canAutoCheckin, blockReason);
    }

    /** {@code auto_checkin_eligibility} — summary for a booking's guest. */
    public GuestEkycStatusSummary autoCheckinEligibility(long bookingId) {
        Map<String, Object> booking = bookings.fetchBooking(bookingId);
        return eligibilityForBooking(booking).summary();
    }

    private record Eligibility(GuestEkycStatusSummary summary, EkycSummaryRecord record) {
    }

    /** {@code eligibility_for_booking}. */
    private Eligibility eligibilityForBooking(Map<String, Object> booking) {
        long guestId = ((Number) booking.get("guest_id")).longValue();
        EkycSummaryRecord record = latestGuestSummaryRecord(guestId);
        GuestEkycStatusSummary summary = record == null
                ? GuestEkycStatusSummary.notSubmitted(guestId)
                : summaryFromRecord(record);
        summary = applyBookingConstraints(summary, String.valueOf(booking.get("status")),
                toLocalDate(booking.get("check_in_date")),
                toLocalDate(booking.get("check_out_date")),
                ((Number) booking.get("room_id")).longValue());
        return new Eligibility(summary, record);
    }

    /** {@code apply_booking_constraints}. */
    private GuestEkycStatusSummary applyBookingConstraints(GuestEkycStatusSummary summary,
            String bookingStatus, LocalDate checkInDate, LocalDate checkOutDate, long roomId) {
        if (!summary.canAutoCheckin()) {
            return summary;
        }
        String reason = bookingStatusBlockReason(bookingStatus);
        if (reason != null) {
            return block(summary, reason);
        }
        LocalDate today = bookings.hotelToday();
        if (checkInDate.isAfter(today)) {
            return block(summary, "Auto check-in opens on " + checkInDate + ".");
        }
        if (checkOutDate.isBefore(today)) {
            return block(summary, "Booking stay dates have passed.");
        }
        String roomStatus = bookings.fetchRoomStatus(roomId);
        if (roomStatus != null) {
            String roomReason = roomStatusBlockReason(roomStatus);
            if (roomReason != null) {
                return block(summary, roomReason);
            }
        }
        String ic = bookings.fetchGuestIcNumber(summary.guestId());
        if (ic == null || ic.trim().isEmpty()) {
            return block(summary,
                    "Add your IC or passport number to your details to check in online.");
        }
        return summary;
    }

    private static GuestEkycStatusSummary block(GuestEkycStatusSummary summary, String reason) {
        return new GuestEkycStatusSummary(summary.guestId(), summary.ekycVerificationId(),
                summary.status(), summary.selfCheckinEnabled(), summary.verifiedAt(), false, reason);
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

    private static String bookingStatusBlockReason(String status) {
        return switch (status) {
            case "confirmed" -> null;
            case "pending", "pending_payment" -> "Payment required before check-in.";
            case "pending_confirmation" -> "Payment confirmation is required before check-in.";
            case "checked_in", "auto_checked_in" -> "Booking is already checked in.";
            case "checked_out", "completed" -> "Booking has already checked out.";
            case "voided", "cancelled", "canceled" -> "Booking is not active.";
            default -> "Booking status is " + status.replace('_', ' ') + ".";
        };
    }

    private static String roomStatusBlockReason(String status) {
        return switch (status) {
            case "dirty", "cleaning", "reserved_dirty" ->
                "Cannot auto check-in - the room must be cleaned before check-in.";
            case "maintenance", "out_of_order" ->
                "Cannot auto check-in - room is currently under " + status.replace('_', ' ') + ".";
            default -> null;
        };
    }

    /** {@code auto_checkin_for_guest_portal}. */
    public AutoCheckinResponse autoCheckinForGuestPortal(long bookingId) {
        Map<String, Object> booking = bookings.fetchBooking(bookingId);
        Eligibility eligibility = eligibilityForBooking(booking);
        EkycSummaryRecord record = eligibility.record();
        if (record == null) {
            throw ApiError.badRequest(eligibility.summary().autoCheckinBlockReason() != null
                    ? eligibility.summary().autoCheckinBlockReason()
                    : "Approved eKYC is required for auto check-in");
        }
        return performAutoCheckin(record.userId(), booking, eligibility.summary(), record);
    }

    /** {@code perform_auto_checkin_with_summary}. */
    private AutoCheckinResponse performAutoCheckin(long actorUserId, Map<String, Object> booking,
            GuestEkycStatusSummary summary, EkycSummaryRecord record) {
        if (!summary.canAutoCheckin()) {
            throw ApiError.badRequest(summary.autoCheckinBlockReason() != null
                    ? summary.autoCheckinBlockReason()
                    : "Booking is not eligible for auto check-in");
        }
        long bookingId = ((Number) booking.get("id")).longValue();
        long guestId = ((Number) booking.get("guest_id")).longValue();
        long roomId = ((Number) booking.get("room_id")).longValue();

        OffsetDateTime checkedInAt = paymentTx.checkinBookingFlow(actorUserId, booking,
                "Guest checked in through approved eKYC auto check-in",
                new SelfCheckinEvent(guestId, record.verificationId(), AUTO_CHECKIN_SOURCE,
                        null, null));
        String roomNumber = bookings.roomNumber(roomId);

        return new AutoCheckinResponse(true, bookingId, roomNumber, true,
                checkedInAt, summary,
                "Successfully checked in to room " + roomNumber
                        + ". Your digital key has been sent.");
    }

    /** {@code SelfCheckinEventInsert} — the self-check-in event payload. */
    public record SelfCheckinEvent(long guestId, long ekycVerificationId, String source,
            String deviceType, String checkinLocation) {
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }
}
