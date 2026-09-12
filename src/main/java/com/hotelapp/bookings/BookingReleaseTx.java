package com.hotelapp.bookings;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.portal.PortalBookingOps;
import com.hotelapp.portal.PortalPaymentTx;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code perform_release} — the release itself, shared shape with the
 * scheduled sweep upstream so the two can never drift in what they write.
 * One transaction: void the booking, free the room, void uncompleted
 * payments, restore comp credits, recompute payment status, then history,
 * modification row and audit event.
 */
@Component
public class BookingReleaseTx {

    public record ReleaseOutcome(int nightsCredited,
            List<LocalDate> affectedNightAuditDates) {
    }

    private final PortalBookingOps bookings;
    private final PortalPaymentTx payments;
    private final AuditWriter audit;

    public BookingReleaseTx(PortalBookingOps bookings, PortalPaymentTx payments,
            AuditWriter audit) {
        this.bookings = bookings;
        this.payments = payments;
        this.audit = audit;
    }

    @Transactional
    public ReleaseOutcome performRelease(Map<String, Object> booking, Long actor,
            String reason, boolean automated) {
        long bookingId = ((Number) booking.get("id")).longValue();
        List<LocalDate> affectedDates = bookings.bookingNightAuditDates(bookingId);

        bookings.voidBooking(bookingId, actor);
        Object roomId = booking.get("room_id");
        if (roomId != null) {
            bookings.releaseRoom(((Number) roomId).longValue());
        }
        bookings.voidUncompletedPayments(bookingId);
        int nightsCredited = bookings.restoreComplimentaryCredits(booking);
        payments.recomputePaymentStatus(bookingId);

        Map<String, Object> history = new LinkedHashMap<>();
        history.put("action", automated ? "auto_released_unpaid" : "released_unpaid");
        history.put("room_id", roomId);
        history.put("guest_id", booking.get("guest_id"));
        history.put("check_in_date", String.valueOf(booking.get("check_in_date")));
        history.put("check_out_date", String.valueOf(booking.get("check_out_date")));
        history.put("complimentary_nights_restored", nightsCredited);
        bookings.recordBookingHistory(bookingId, String.valueOf(booking.get("status")),
                "voided", actor, reason, history);

        // booking_modifications.modified_by is NOT NULL, so an automated release
        // cannot have a row here — there is no user to attribute it to.
        if (actor != null) {
            bookings.recordVoidModification(booking, actor);
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("booking_number", booking.get("booking_number"));
        details.put("reason", reason);
        details.put("room_id", roomId);
        Object total = booking.get("total_amount");
        details.put("total_amount", total == null ? null : total.toString());
        details.put("complimentary_nights_restored", nightsCredited);
        audit.event(actor, automated ? "booking.auto_released_unpaid"
                : "booking.released_unpaid", "booking", bookingId, details);

        return new ReleaseOutcome(nightsCredited, affectedDates);
    }
}
