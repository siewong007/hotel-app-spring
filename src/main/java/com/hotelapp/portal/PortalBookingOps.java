package com.hotelapp.portal;

import com.hotelapp.core.error.ApiError;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of the booking-lifecycle repository helpers the guest portal flows ride
 * on ({@code repositories/bookings.rs}): the void/cancel transaction pieces,
 * the check-in transition pieces, and the night-audit date lookup.
 *
 * Every method runs on the ambient JdbcTemplate connection, so callers inside
 * {@code @Transactional} get the upstream {@code *_tx} semantics for free.
 */
@Component
public class PortalBookingOps {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PortalBookingOps(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** {@code fetch_booking_by_id} — full booking row or 404. */
    public Map<String, Object> fetchBooking(long bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM bookings WHERE id = ?", bookingId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
        return rows.get(0);
    }

    /** {@code user_owns_booking}. */
    public boolean userOwnsBooking(long userId, long guestId) {
        Boolean owns = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM user_guests ug WHERE ug.user_id = ? AND ug.guest_id = ?
                    UNION
                    SELECT 1 FROM users u WHERE u.id = ? AND u.guest_id = ?)
                """, Boolean.class, userId, guestId, userId, guestId);
        return Boolean.TRUE.equals(owns);
    }

    /** {@code void_booking_tx} — refuses a second void. */
    public void voidBooking(long bookingId, Long userId) {
        int updated = jdbc.update("""
                UPDATE bookings
                SET status = 'voided', updated_at = CURRENT_TIMESTAMP,
                    cancelled_at = CURRENT_TIMESTAMP, cancelled_by = ?
                WHERE id = ? AND status != 'voided'
                """, userId, bookingId);
        if (updated != 1) {
            throw ApiError.badRequest("Booking cannot be voided");
        }
    }

    /** {@code release_room_tx}. */
    public void releaseRoom(long roomId) {
        jdbc.update("UPDATE rooms SET status = 'available' WHERE id = ?", roomId);
    }

    /** {@code void_uncompleted_booking_payments_tx} — keeps completed rows. */
    public void voidUncompletedPayments(long bookingId) {
        jdbc.update("UPDATE payments SET status = 'void' WHERE booking_id = ? "
                + "AND status NOT IN ('void', 'completed')", bookingId);
    }

    /** {@code completed_booking_payment_total} — completed non-refund sum. */
    public java.math.BigDecimal completedBookingPaymentTotal(long bookingId) {
        java.math.BigDecimal total = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount) FILTER (
                    WHERE status = 'completed'
                      AND COALESCE(payment_type, 'booking') != 'refund'), 0)
                FROM payments WHERE booking_id = ?
                """, java.math.BigDecimal.class, bookingId);
        return total == null ? java.math.BigDecimal.ZERO : total;
    }

    /**
     * {@code restore_complimentary_credits_tx} — return comp-night credits to
     * the guest on void/release. Returns the nights credited (0 when the
     * booking was not complimentary or the room's type is gone).
     */
    public int restoreComplimentaryCredits(Map<String, Object> booking) {
        if (!Boolean.TRUE.equals(booking.get("is_complimentary"))) {
            return 0;
        }
        LocalDate checkIn = toLocalDate(booking.get("check_in_date"));
        LocalDate checkOut = toLocalDate(booking.get("check_out_date"));
        int nights = (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS
                .between(checkIn, checkOut));
        Object roomId = booking.get("room_id");
        if (roomId == null) {
            return 0;
        }
        List<Long> roomTypeIds = jdbc.query(
                "SELECT room_type_id FROM rooms WHERE id = ?",
                (rs, i) -> rs.getLong(1), ((Number) roomId).longValue());
        if (roomTypeIds.isEmpty()) {
            return 0;
        }
        jdbc.update("""
                INSERT INTO guest_complimentary_credits
                    (guest_id, room_type_id, nights_available, notes, created_at, updated_at)
                VALUES (?, ?, ?, 'Refunded from voided complimentary booking',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (guest_id, room_type_id)
                DO UPDATE SET nights_available =
                    guest_complimentary_credits.nights_available + EXCLUDED.nights_available,
                    updated_at = CURRENT_TIMESTAMP
                """, ((Number) booking.get("guest_id")).longValue(),
                roomTypeIds.get(0), nights);
        return nights;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) {
            return d;
        }
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    /** {@code record_booking_history_tx}. */
    public void recordBookingHistory(long bookingId, String previousStatus, String newStatus,
            Long changedBy, String changeReason, Map<String, Object> metadata) {
        jdbc.update("""
                INSERT INTO booking_history
                    (booking_id, previous_status, new_status, changed_by, change_reason, metadata)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, bookingId, previousStatus, newStatus, changedBy, changeReason,
                metadata == null ? null : objectMapper.writeValueAsString(metadata));
    }

    /** {@code record_booking_void_modification_tx}. */
    public void recordVoidModification(Map<String, Object> booking, long userId) {
        jdbc.update("""
                INSERT INTO booking_modifications
                    (booking_id, modification_type, old_value, new_value, modified_by)
                VALUES (?, 'voided', CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """,
                ((Number) booking.get("id")).longValue(),
                objectMapper.writeValueAsString(Map.of(
                        "status", String.valueOf(booking.get("status")),
                        "check_in_date", String.valueOf(booking.get("check_in_date")),
                        "check_out_date", String.valueOf(booking.get("check_out_date")))),
                objectMapper.writeValueAsString(Map.of("status", "voided")),
                userId);
    }

    /** {@code record_checkin_modification_tx}. */
    public void recordCheckinModification(Map<String, Object> booking, long userId) {
        jdbc.update("""
                INSERT INTO booking_modifications
                    (booking_id, modification_type, old_value, new_value, modified_by)
                VALUES (?, 'check_in', CAST(? AS jsonb), CAST(? AS jsonb), ?)
                """,
                ((Number) booking.get("id")).longValue(),
                objectMapper.writeValueAsString(Map.of(
                        "status", String.valueOf(booking.get("status")),
                        "guest_id", booking.get("guest_id"),
                        "room_id", booking.get("room_id"))),
                objectMapper.writeValueAsString(Map.of(
                        "status", "checked_in",
                        "guest_id", booking.get("guest_id"),
                        "room_id", booking.get("room_id"))),
                userId);
    }

    /** {@code booking_night_audit_dates}. */
    public List<LocalDate> bookingNightAuditDates(long bookingId) {
        return jdbc.query("""
                SELECT DISTINCT audit_date FROM (
                    SELECT napn.audit_date FROM night_audit_posted_nights napn
                    WHERE napn.booking_id = ?
                    UNION
                    SELECT nar.audit_date
                    FROM night_audit_details nad
                    JOIN night_audit_runs nar ON nar.id = nad.audit_run_id
                    WHERE nad.booking_id = ? AND nad.record_type = 'booking' AND nad.action = 'posted'
                    UNION
                    SELECT b.posted_date AS audit_date FROM bookings b
                    WHERE b.id = ? AND COALESCE(b.is_posted, false) AND b.posted_date IS NOT NULL
                ) posted ORDER BY audit_date
                """, (rs, i) -> rs.getObject(1, LocalDate.class),
                bookingId, bookingId, bookingId);
    }

    /** {@code fetch_room_status}. */
    public String fetchRoomStatus(long roomId) {
        List<String> rows = jdbc.query("SELECT status FROM rooms WHERE id = ?",
                (rs, i) -> rs.getString(1), roomId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code room_number}. */
    public String roomNumber(long roomId) {
        List<String> rows = jdbc.query("SELECT room_number FROM rooms WHERE id = ?",
                (rs, i) -> rs.getString(1), roomId);
        if (rows.isEmpty()) {
            throw ApiError.internal("Room not found");
        }
        return rows.get(0);
    }

    /** {@code fetch_guest_ic_number}. */
    public String fetchGuestIcNumber(long guestId) {
        List<String> rows = jdbc.query("SELECT ic_number FROM guests WHERE id = ?",
                (rs, i) -> rs.getString(1), guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code checkin_booking_tx} — atomic guard on status = 'confirmed'. */
    public void checkinBooking(long bookingId) {
        int updated = jdbc.update("""
                UPDATE bookings
                SET status = 'checked_in', actual_check_in = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'confirmed'
                """, bookingId);
        if (updated != 1) {
            throw ApiError.badRequest("Booking cannot be checked in");
        }
    }

    /** {@code confirm_booking_tx} — idempotent payment-awaiting -> confirmed. */
    public boolean confirmBooking(long bookingId) {
        return jdbc.update("""
                UPDATE bookings SET status = 'confirmed', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('pending', 'pending_payment', 'pending_confirmation')
                """, bookingId) == 1;
    }

    /** {@code move_booking_to_pending_confirmation_tx}. */
    public boolean moveToPendingConfirmation(long bookingId) {
        return jdbc.update("""
                UPDATE bookings SET status = 'pending_confirmation', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('pending', 'pending_payment')
                """, bookingId) == 1;
    }

    /** {@code record_online_checkin_payment_tx} — auto-post the owed balance. */
    public boolean recordOnlineCheckinPayment(Map<String, Object> booking, long userId) {
        Object paymentMethod = booking.get("payment_method");
        String method = paymentMethod == null ? "online_banking" : paymentMethod.toString();
        return jdbc.update("""
                INSERT INTO payments (booking_id, amount, payment_method, payment_type,
                    status, notes, created_by)
                SELECT b.id,
                       b.total_amount - COALESCE((SELECT SUM(p.amount) FROM payments p
                           WHERE p.booking_id = b.id AND p.status = 'completed'
                             AND COALESCE(p.payment_type, 'booking') != 'refund'), 0),
                       ?, 'booking', 'completed',
                       'Auto-recorded at check-in for online reservation', ?
                FROM bookings b
                WHERE b.id = ?
                  AND b.total_amount - COALESCE((SELECT SUM(p.amount) FROM payments p
                           WHERE p.booking_id = b.id AND p.status = 'completed'
                             AND COALESCE(p.payment_type, 'booking') != 'refund'), 0) > 0
                """, method, userId, ((Number) booking.get("id")).longValue()) > 0;
    }

    /** {@code set_room_occupied_tx}. */
    public void setRoomOccupied(long roomId) {
        jdbc.update("UPDATE rooms SET status = 'occupied' WHERE id = ?", roomId);
    }

    /** {@code record_self_checkin_event_tx} — returns checked_in_at. */
    public OffsetDateTime recordSelfCheckinEvent(long bookingId, long guestId,
            long ekycVerificationId, long userId, String source, String deviceType,
            String checkinLocation) {
        String eventData = objectMapper.writeValueAsString(Map.of(
                "source", source, "guest_id", guestId,
                "ekyc_verification_id", ekycVerificationId));
        return jdbc.queryForObject("""
                INSERT INTO self_checkin_events (
                    booking_id, guest_id, ekyc_verification_id, user_id, checked_in_at,
                    room_key_issued, digital_key_sent, device_type, checkin_location,
                    event_type, source, event_data, created_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, true, true, ?, ?, 'auto_checkin', ?,
                    CAST(? AS jsonb), CURRENT_TIMESTAMP)
                RETURNING checked_in_at
                """, OffsetDateTime.class, bookingId, guestId, ekycVerificationId,
                userId, deviceType, checkinLocation, source, eventData);
    }

    /** {@code find_guest_user_id} — the active guest account for a guest. */
    public Long findGuestUserId(long guestId) {
        List<Long> rows = jdbc.query("""
                SELECT id FROM users WHERE guest_id = ? AND user_type::text = 'guest'
                AND is_active = true ORDER BY id LIMIT 1
                """, (rs, i) -> rs.getLong(1), guestId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** hotel_today — the connection tz carries the hotel business day. */
    public LocalDate hotelToday() {
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        if (today == null) {
            throw ApiError.internal("Database clock unavailable");
        }
        return today;
    }
}
