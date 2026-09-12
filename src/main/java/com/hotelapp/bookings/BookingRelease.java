package com.hotelapp.bookings;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.portal.PortalBookingOps;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code release_pending_payment_booking} (services/bookings.rs) — release the
 * room held by a booking that was never paid for, recording why.
 *
 * Deliberately narrower than void: only a {@code pending_payment} booking
 * qualifies — anything else is refused and pointed at void — and money already
 * collected is refused outright, because deciding a refund is not this action's
 * job. The reason is required and ends up in booking history and the audit log.
 */
@Component
public class BookingRelease {

    private static final int MIN_RELEASE_REASON_LEN = 4;
    private static final int MAX_RELEASE_REASON_LEN = 500;

    /** {@code UNPAID_HOLD_SETTING} — hours an unpaid booking keeps its room. */
    private static final String UNPAID_HOLD_SETTING = "unpaid_hold_release_hours";

    /** {@code MAX_RELEASES_PER_SWEEP} — most holds one sweep will release. */
    private static final long MAX_RELEASES_PER_SWEEP = 200;

    private static final Logger log = LoggerFactory.getLogger(BookingRelease.class);

    private final JdbcTemplate jdbc;
    private final PortalBookingOps bookings;
    private final BookingReleaseTx tx;
    private final HotelSettings settings;

    public BookingRelease(JdbcTemplate jdbc, PortalBookingOps bookings,
            BookingReleaseTx tx, HotelSettings settings) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.tx = tx;
        this.settings = settings;
    }

    /** {@code validate_release_reason}. */
    public static String validateReleaseReason(String reason) {
        String clean = Sanitizer.sanitizeNotes(reason == null ? "" : reason).trim();
        if (clean.codePointCount(0, clean.length()) < MIN_RELEASE_REASON_LEN) {
            throw ApiError.badRequest("Please give a reason for releasing this booking.");
        }
        if (clean.codePointCount(0, clean.length()) > MAX_RELEASE_REASON_LEN) {
            throw ApiError.badRequest(
                    "The reason must be " + MAX_RELEASE_REASON_LEN + " characters or fewer.");
        }
        return clean;
    }

    public Map<String, Object> releasePendingPaymentBooking(long userId, long bookingId,
            String reason) {
        String clean = validateReleaseReason(reason);
        Map<String, Object> booking = bookings.fetchBooking(bookingId);
        String status = String.valueOf(booking.get("status"));

        if (!"pending_payment".equals(status)) {
            throw ApiError.badRequest("Only bookings awaiting payment can be released; "
                    + "this one is '" + status + "'. Void it instead if it has to be cancelled.");
        }

        BigDecimal collected = bookings.completedBookingPaymentTotal(bookingId);
        if (collected.compareTo(BigDecimal.ZERO) > 0) {
            throw ApiError.conflict("Payments have been recorded against this booking. "
                    + "Void it through the refund flow instead of releasing it.");
        }

        BookingReleaseTx.ReleaseOutcome outcome =
                tx.performRelease(booking, userId, clean, false);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Room released. The booking is now voided.");
        response.put("booking_id", bookingId);
        response.put("reason", clean);
        response.put("complimentary_nights_restored", outcome.nightsCredited());
        response.put("affected_night_audit_dates", outcome.affectedNightAuditDates());
        response.put("night_audit_rerun_required",
                !outcome.affectedNightAuditDates().isEmpty());
        return response;
    }

    // ------------------------------------------------------------------
    // Automated sweep (release_stale_unpaid_holds)
    // ------------------------------------------------------------------

    /**
     * {@code is_auto_releasable_source} — only online-channel holds auto-
     * release; a front-desk hold is left alone no matter how stale. Separate
     * from the check-in {@code is_online_source} on purpose.
     */
    public static boolean isAutoReleasableSource(String source) {
        if (source == null) {
            return false;
        }
        String value = source.trim();
        return value.equalsIgnoreCase("website") || value.equalsIgnoreCase("online");
    }

    /** The configured hold window, or 0 when auto-release is off. */
    int unpaidHoldWindowHours() {
        return settings.getPositiveInt(UNPAID_HOLD_SETTING, 0);
    }

    /** {@code stale_unpaid_hold_ids}. */
    List<Long> staleUnpaidHoldIds(int holdHours, long limit) {
        return jdbc.query("""
                SELECT b.id FROM bookings b
                WHERE b.status = 'pending_payment'
                  AND LOWER(BTRIM(COALESCE(b.source, ''))) IN ('website', 'online')
                  AND b.created_at < CURRENT_TIMESTAMP - make_interval(hours => ?)
                  AND COALESCE((
                      SELECT SUM(p.amount) FROM payments p
                      WHERE p.booking_id = b.id
                        AND p.status = 'completed'
                        AND COALESCE(p.payment_type, 'booking') != 'refund'
                  ), 0) = 0
                ORDER BY b.created_at LIMIT ?
                """, (rs, i) -> rs.getLong(1), holdHours, limit);
    }

    /**
     * {@code release_stale_unpaid_holds} — release stale unpaid holds,
     * returning how many were released. Each candidate is re-checked under
     * current state because a guest can pay between the sweep's query and its
     * write; one failure is logged and skipped so the rest still clear.
     */
    public long releaseStaleUnpaidHolds() {
        int holdHours = unpaidHoldWindowHours();
        if (holdHours <= 0) {
            return 0;
        }
        List<Long> candidates = staleUnpaidHoldIds(holdHours, MAX_RELEASES_PER_SWEEP);
        String reason = "Automatically released: unpaid for more than "
                + holdHours + " hour(s)";

        long released = 0;
        for (long bookingId : candidates) {
            Map<String, Object> booking;
            try {
                booking = bookings.fetchBooking(bookingId);
            } catch (ApiError error) {
                log.warn("Auto-release skipped booking {}: {}", bookingId, error.getMessage());
                continue;
            }
            if (!"pending_payment".equals(String.valueOf(booking.get("status")))) {
                continue;
            }
            // Front-desk holds are exempt. The SQL already filters on this;
            // the helper stays authoritative so the rule has one definition.
            Object source = booking.get("source");
            if (!isAutoReleasableSource(source == null ? null : String.valueOf(source))) {
                continue;
            }
            try {
                if (bookings.completedBookingPaymentTotal(bookingId)
                        .compareTo(BigDecimal.ZERO) > 0) {
                    continue;
                }
            } catch (Exception error) {
                log.warn("Auto-release could not verify payments on booking {}: {}",
                        bookingId, error.getMessage());
                continue;
            }
            try {
                tx.performRelease(booking, null, reason, true);
                released++;
            } catch (Exception error) {
                log.warn("Auto-release failed for booking {}: {}", bookingId, error.getMessage());
            }
        }
        return released;
    }
}
