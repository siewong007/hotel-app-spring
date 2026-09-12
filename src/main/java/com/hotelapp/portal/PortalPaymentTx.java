package com.hotelapp.portal;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped payment writes for the guest portal. Kept as a separate
 * bean so {@link PortalPayments}' orchestration methods (which call out to
 * PayPal between transactions) invoke these through the Spring proxy — matching
 * upstream's explicit {@code pool.begin()}/{@code tx.commit()} boundaries.
 */
@Component
public class PortalPaymentTx {

    private final JdbcTemplate jdbc;
    private final PortalBookingOps bookings;
    private final AuditWriter audit;

    public PortalPaymentTx(JdbcTemplate jdbc, PortalBookingOps bookings, AuditWriter audit) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.audit = audit;
    }

    void lockBookingForPayment(long bookingId) {
        Long id = jdbc.query("SELECT id FROM bookings WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null, bookingId);
        if (id == null) {
            throw ApiError.notFound("Booking not found");
        }
    }

    private String bookingStatusForPayment(long bookingId) {
        List<String> rows = jdbc.query("SELECT status FROM bookings WHERE id = ?",
                (rs, i) -> rs.getString(1), bookingId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
        return rows.get(0);
    }

    private void ensureBookingAwaitingPayment(String status) {
        if (!List.of("pending", "pending_payment").contains(status)) {
            throw ApiError.badRequest("This booking is not awaiting payment.");
        }
    }

    private void ensureNoActiveBookingPayment(long bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT payment_method, status FROM payments
                WHERE booking_id = ? AND payment_type = 'booking'
                  AND status IN ('pending', 'processing', 'completed')
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, bookingId);
        if (rows.isEmpty()) {
            return;
        }
        String method = String.valueOf(rows.get(0).get("payment_method"));
        String status = String.valueOf(rows.get(0).get("status"));
        if ("paypal".equals(method) && "pending".equals(status)) {
            throw ApiError.conflict("A PayPal payment for this booking is already pending. "
                    + "Please wait up to 10 minutes before trying again.");
        }
        throw ApiError.conflict("A payment for this booking is already pending or completed.");
    }

    long insertPendingPayment(long bookingId, BigDecimal amount, String currency,
            String paymentMethod, String gateway, String description) {
        Long id = jdbc.queryForObject("""
                INSERT INTO payments (booking_id, amount, currency, payment_method, payment_type,
                    payment_gateway, status, notes, created_by)
                VALUES (?, ?, ?, ?, 'booking', ?, 'pending', ?, NULL)
                RETURNING id
                """, Long.class, bookingId, amount, currency, paymentMethod, gateway, description);
        if (id == null) {
            throw ApiError.internal("Payment insert failed");
        }
        return id;
    }

    /** {@code create_bank_transfer_claim}. */
    @Transactional
    public PaymentActionResponse createBankTransferClaim(Map<String, Object> booking) {
        return createBankTransferClaim(booking, null);
    }

    /**
     * {@code consume_capability_tx} — spend the recovery capability inside the
     * caller's transaction, recording the payment it produced. The guard lives
     * in the WHERE clause, so two concurrent submissions cannot both pass it.
     */
    private boolean consumeCapability(long capabilityId, long replacementPaymentId) {
        return jdbc.update("""
                UPDATE payment_retry_capabilities
                SET consumed_at = CURRENT_TIMESTAMP, replacement_payment_id = ?
                WHERE id = ? AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP
                """, replacementPaymentId, capabilityId) == 1;
    }

    /**
     * {@code restore_capability} — give back a capability whose payment was
     * released after PayPal refused the order. Scoped to the payment it was
     * spent on, so it can never resurrect a capability that funded a
     * different, live payment.
     */
    public boolean restoreCapability(long capabilityId, long spentOnPaymentId) {
        return jdbc.update("""
                UPDATE payment_retry_capabilities
                SET consumed_at = NULL, replacement_payment_id = NULL
                WHERE id = ? AND replacement_payment_id = ?
                """, capabilityId, spentOnPaymentId) == 1;
    }

    /**
     * {@code create_bank_transfer_claim_inner} — the whole DB flow is one tx.
     * {@code capabilityId} spends the emailed recovery capability inside the
     * same transaction as the insert: if it turns out already spent, the whole
     * transaction rolls back and the guest is never charged twice.
     */
    @Transactional
    public PaymentActionResponse createBankTransferClaim(Map<String, Object> booking,
            Long capabilityId) {
        long bookingId = ((Number) booking.get("id")).longValue();
        String currency = PortalPayments.bookingCurrency(booking);
        BigDecimal amount = (BigDecimal) booking.get("total_amount");

        lockBookingForPayment(bookingId);
        ensureBookingAwaitingPayment(bookingStatusForPayment(bookingId));
        ensureNoActiveBookingPayment(bookingId);

        long paymentId = insertPendingPayment(bookingId, amount, currency, "bank_transfer",
                null, "Guest-submitted bank transfer claim (pending staff review)");

        if (bookings.moveToPendingConfirmation(bookingId)) {
            bookings.recordBookingHistory(bookingId, String.valueOf(booking.get("status")),
                    "pending_confirmation", null,
                    "Bank transfer submitted for staff confirmation",
                    Map.of("payment_id", paymentId));
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("booking_id", bookingId);
        details.put("amount", amount == null ? null : amount.toPlainString());
        details.put("method", "bank_transfer");
        details.put("source", "guest_portal");
        audit.event(null, "payment_created", "payment", paymentId, details);

        if (capabilityId != null && !consumeCapability(capabilityId, paymentId)) {
            // Another submission won the race, or the link expired between the
            // lookup and here. Dropping the transaction unwinds the payment.
            throw ApiError.conflict("This payment link has already been used.");
        }

        return new PaymentActionResponse(paymentId, "pending", "pending_confirmation");
    }

    /**
     * {@code create_paypal_order_inner} transaction 1 — insert the pending
     * payment under the booking lock. Commits before the PayPal call so the
     * order can carry our payment id in custom_id.
     */
    @Transactional
    public long insertPendingPaypalPayment(Map<String, Object> booking) {
        return insertPendingPaypalPayment(booking, null);
    }

    /** Same insert, additionally spending the recovery capability in-tx. */
    @Transactional
    public long insertPendingPaypalPayment(Map<String, Object> booking,
            Long capabilityId) {
        long bookingId = ((Number) booking.get("id")).longValue();
        lockBookingForPayment(bookingId);
        ensureBookingAwaitingPayment(bookingStatusForPayment(bookingId));
        ensureNoActiveBookingPayment(bookingId);

        long paymentId = insertPendingPayment(bookingId,
                (BigDecimal) booking.get("total_amount"),
                PortalPayments.bookingCurrency(booking), "paypal", "paypal", null);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("booking_id", bookingId);
        Object amount = booking.get("total_amount");
        details.put("amount", amount == null ? null : amount.toString());
        details.put("method", "paypal");
        details.put("source", "guest_portal");
        audit.event(null, "payment_created", "payment", paymentId, details);

        if (capabilityId != null && !consumeCapability(capabilityId, paymentId)) {
            throw ApiError.conflict("This payment link has already been used.");
        }
        return paymentId;
    }

    /** {@code complete_and_confirm} — complete payment + confirm booking atomically. */
    @Transactional
    public PaymentActionResponse completeAndConfirm(long paymentId, long bookingId,
            Long actorUserId, String auditAction) {
        lockBookingForPayment(bookingId);
        List<String> statuses = jdbc.query(
                "SELECT status FROM payments WHERE id = ? AND booking_id = ? FOR UPDATE",
                (rs, i) -> rs.getString(1), paymentId, bookingId);
        if (statuses.isEmpty()) {
            throw ApiError.notFound("Payment not found.");
        }
        String status = statuses.get(0);
        if (!"pending".equals(status) && !"processing".equals(status)) {
            throw ApiError.badRequest("Payment is no longer pending.");
        }

        Boolean otherCompleted = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM payments WHERE booking_id = ?
                    AND payment_type = 'booking' AND status = 'completed' AND id <> ?)
                """, Boolean.class, bookingId, paymentId);
        if (Boolean.TRUE.equals(otherCompleted)) {
            throw ApiError.conflict("This booking already has a completed payment.");
        }

        Long completed = jdbc.query("""
                UPDATE payments SET status = 'completed', processed_at = CURRENT_TIMESTAMP,
                    processed_by = ?
                WHERE id = ? AND status IN ('pending', 'processing') RETURNING id
                """, rs -> rs.next() ? rs.getLong(1) : null, actorUserId, paymentId);
        if (completed == null) {
            throw ApiError.badRequest("Payment is no longer pending.");
        }

        boolean confirmed = bookings.confirmBooking(bookingId);
        if (confirmed) {
            bookings.recordBookingHistory(bookingId, "pending_payment", "confirmed",
                    actorUserId, "Payment approved", Map.of("payment_id", paymentId));
        }
        recomputePaymentStatus(bookingId);
        audit.event(actorUserId, auditAction, "payment", paymentId,
                Map.of("booking_id", bookingId, "booking_confirmed", confirmed));
        return new PaymentActionResponse(paymentId, "completed", "confirmed");
    }

    /**
     * {@code checkin_booking_flow} reduced to the auto-check-in shape: the
     * portal path never sends a check-in patch or a captured payment.
     */
    @Transactional
    public java.time.OffsetDateTime checkinBookingFlow(long userId, Map<String, Object> booking,
            String historyReason, AutoCheckin.SelfCheckinEvent selfCheckinEvent) {
        long bookingId = ((Number) booking.get("id")).longValue();
        String status = String.valueOf(booking.get("status"));
        if ("pending".equals(status)) {
            throw ApiError.badRequest("Payment required before check-in.");
        }
        if (!"confirmed".equals(status)) {
            throw ApiError.badRequest("Cannot check in booking with status: " + status);
        }

        long roomId = ((Number) booking.get("room_id")).longValue();
        String roomStatus = bookings.fetchRoomStatus(roomId);
        if (roomStatus != null
                && List.of("maintenance", "out_of_order", "dirty", "cleaning", "reserved_dirty")
                        .contains(roomStatus)) {
            String reason = List.of("dirty", "cleaning", "reserved_dirty").contains(roomStatus)
                    ? "the room must be cleaned before check-in"
                    : "room is currently under " + roomStatus.replace('_', ' ');
            throw ApiError.badRequest("Cannot check in - " + reason + ".");
        }

        long guestId = ((Number) booking.get("guest_id")).longValue();
        String ic = bookings.fetchGuestIcNumber(guestId);
        if (ic == null || ic.trim().isEmpty()) {
            throw ApiError.badRequest("IC / passport number is required to complete check-in");
        }

        bookings.checkinBooking(bookingId);

        boolean autoOnlinePaymentRecorded = false;
        Object source = booking.get("source");
        if (source != null && "online".equalsIgnoreCase(source.toString().trim())) {
            autoOnlinePaymentRecorded = bookings.recordOnlineCheckinPayment(booking, userId);
            if (autoOnlinePaymentRecorded) {
                recomputePaymentStatus(bookingId);
            }
        }

        java.time.LocalDate today = bookings.hotelToday();
        Object checkOut = booking.get("check_out_date");
        java.time.LocalDate checkOutDate = checkOut instanceof java.time.LocalDate date
                ? date
                : checkOut instanceof java.sql.Date date
                        ? date.toLocalDate()
                        : java.time.LocalDate.parse(String.valueOf(checkOut).substring(0, 10));
        if (!checkOutDate.isBefore(today)) {
            bookings.setRoomOccupied(roomId);
        }

        Map<String, Object> historyMetadata = new LinkedHashMap<>();
        historyMetadata.put("guest_id", guestId);
        historyMetadata.put("room_id", roomId);
        historyMetadata.put("payment_recorded", 0.0);
        historyMetadata.put("auto_online_payment_recorded", autoOnlinePaymentRecorded);
        historyMetadata.put("source", AutoCheckin.AUTO_CHECKIN_SOURCE);
        historyMetadata.put("ekyc_verification_id",
                selfCheckinEvent == null ? null : selfCheckinEvent.ekycVerificationId());
        bookings.recordBookingHistory(bookingId, status, "checked_in", userId, historyReason,
                historyMetadata);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("guest_id", guestId);
        auditDetails.put("room_id", roomId);
        auditDetails.put("source", AutoCheckin.AUTO_CHECKIN_SOURCE);
        auditDetails.put("ekyc_verification_id",
                selfCheckinEvent == null ? null : selfCheckinEvent.ekycVerificationId());
        audit.event(userId,
                selfCheckinEvent == null ? "booking_checkin" : "booking_ekyc_auto_checkin",
                "booking", bookingId, auditDetails);

        bookings.recordCheckinModification(booking, userId);

        java.time.OffsetDateTime selfCheckinAt = null;
        if (selfCheckinEvent != null) {
            selfCheckinAt = bookings.recordSelfCheckinEvent(bookingId, selfCheckinEvent.guestId(),
                    selfCheckinEvent.ekycVerificationId(), userId, selfCheckinEvent.source(),
                    selfCheckinEvent.deviceType(), selfCheckinEvent.checkinLocation());
        }
        return selfCheckinAt != null ? selfCheckinAt : java.time.OffsetDateTime.now();
    }

    /** {@code cancel_pending_booking_by_guest} — the whole cancel is one tx. */
    @Transactional
    public Map<String, Object> cancelPendingBookingByGuest(long userId, long bookingId,
            String reason) {
        Map<String, Object> booking = bookings.fetchBooking(bookingId);
        long guestId = ((Number) booking.get("guest_id")).longValue();
        if (!bookings.userOwnsBooking(userId, guestId)) {
            throw ApiError.forbidden("You don't have permission to cancel this booking");
        }
        String status = String.valueOf(booking.get("status"));
        if (!java.util.Set.of("pending", "pending_payment", "pending_confirmation", "confirmed")
                .contains(status)) {
            throw ApiError.badRequest("Only upcoming bookings can be cancelled online.");
        }
        List<java.time.LocalDate> affectedDates = bookings.bookingNightAuditDates(bookingId);
        long roomId = ((Number) booking.get("room_id")).longValue();

        bookings.voidBooking(bookingId, userId);
        bookings.releaseRoom(roomId);
        bookings.voidUncompletedPayments(bookingId);
        recomputePaymentStatus(bookingId);
        bookings.recordBookingHistory(bookingId, status, "voided", userId,
                reason != null ? reason : "Booking cancelled by guest",
                Map.of("room_id", roomId, "guest_id", guestId,
                        "check_in_date", String.valueOf(booking.get("check_in_date")),
                        "check_out_date", String.valueOf(booking.get("check_out_date"))));
        bookings.recordVoidModification(booking, userId);
        audit.event(userId, "booking_voided", "booking", bookingId, null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Booking cancelled successfully");
        response.put("booking_id", bookingId);
        response.put("affected_night_audit_dates",
                affectedDates.stream().map(java.time.LocalDate::toString).toList());
        response.put("night_audit_rerun_required", !affectedDates.isEmpty());
        return response;
    }

    /** {@code recompute_booking_payment_status_tx} — the shared status write. */
    public void recomputePaymentStatus(long bookingId) {
        jdbc.update("""
                UPDATE bookings AS b
                SET payment_status = CASE
                    WHEN b.status = 'voided' THEN 'void'
                    WHEN COALESCE(b.is_complimentary, false) THEN COALESCE(b.payment_status, 'paid')
                    WHEN b.total_amount <= 0 THEN 'paid'
                    WHEN COALESCE((SELECT SUM(p.amount) FROM payments p
                            WHERE p.booking_id = b.id AND p.status = 'completed'
                              AND COALESCE(p.payment_type, 'booking') != 'refund'), 0)
                         >= b.total_amount THEN 'paid'
                    WHEN COALESCE((SELECT SUM(p.amount) FROM payments p
                            WHERE p.booking_id = b.id AND p.status = 'completed'
                              AND COALESCE(p.payment_type, 'booking') != 'refund'), 0) > 0
                        THEN 'partial'
                    ELSE 'unpaid'
                END,
                updated_at = CURRENT_TIMESTAMP
                WHERE b.id = ?
                """, bookingId);
    }
}
