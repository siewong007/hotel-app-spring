package com.hotelapp.guestbooking;

import com.hotelapp.consent.Consents;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.guestbooking.FunnelModels.BookingInsert;
import com.hotelapp.guestbooking.FunnelModels.GuestContact;
import com.hotelapp.guestbooking.FunnelModels.ValidatedAnonymousGuest;
import com.hotelapp.guestbooking.FunnelModels.VoucherPricing;
import com.hotelapp.portal.PortalAuth;
import com.hotelapp.portal.PortalBookingOps;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * The transaction-scoped half of {@code modules/guest_booking/service.rs}:
 * everything upstream runs inside {@code pool.begin()..tx.commit()} lives here
 * on a separate bean so the proxy boundary is honoured.
 *
 * Repository counterparts: {@code GuestBookingRepository::ensure_online_room_available_tx},
 * {@code allocate_room_tx}, {@code insert_booking_tx}, {@code insert_anonymous_guest_tx},
 * {@code issue_access_token_tx}, {@code redeem_*_tx}, {@code mark_room_reserved_tx},
 * {@code upsert_online_inventory} — plus {@code bookings::record_booking_history_tx}
 * and {@code communications::repository::insert_delivery_tx}.
 */
@Component
public class FunnelBookingTx {

    private static final String ACTIVE_BOOKING_STATUSES =
            "'reserved', 'confirmed', 'checked_in', 'auto_checked_in', 'pending', "
                    + "'pending_payment', 'pending_confirmation'";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PortalBookingOps bookingOps;
    private final Consents consents;
    private final AuditWriter audit;

    public FunnelBookingTx(JdbcTemplate jdbc, ObjectMapper objectMapper,
            PortalBookingOps bookingOps, Consents consents, AuditWriter audit) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.bookingOps = bookingOps;
        this.consents = consents;
        this.audit = audit;
    }

    /** {@code lock_room_type_tx}. */
    public void lockRoomType(long roomTypeId) {
        List<Long> rows = jdbc.query("SELECT id FROM room_types WHERE id = ? FOR UPDATE",
                (rs, i) -> rs.getLong(1), roomTypeId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Room type not found");
        }
    }

    /** {@code ensure_online_room_available_tx}. */
    public void ensureOnlineRoomAvailable(long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        lockRoomType(roomTypeId);
        Map<String, Object> allocation = jdbc.queryForMap("""
                SELECT COALESCE(MAX(walk_in_reserved_rooms), 0)::bigint AS reserved,
                       COALESCE(BOOL_AND(online_booking_enabled), true) AS enabled
                FROM online_inventory_allocations
                WHERE room_type_id = ? AND stay_date >= ? AND stay_date < ?
                """, roomTypeId, checkIn, checkOut);
        long reserved = ((Number) allocation.get("reserved")).longValue();
        boolean enabled = Boolean.TRUE.equals(allocation.get("enabled"));
        if (!enabled) {
            throw ApiError.conflict("Online booking was closed for one or more selected dates. "
                    + "Please review the refreshed availability.");
        }
        Long available = jdbc.queryForObject(
                "SELECT COUNT(*)::bigint FROM rooms r WHERE r.room_type_id = ? "
                        + "AND r.is_active = true "
                        + "AND COALESCE(r.status, 'available') NOT IN ('maintenance', 'out_of_order') "
                        + "AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id = r.id "
                        + "AND b.status IN (" + ACTIVE_BOOKING_STATUSES + ") "
                        + "AND b.check_in_date < ? AND b.check_out_date > ?)",
                Long.class, roomTypeId, checkOut, checkIn);
        if (available == null || available <= reserved) {
            throw ApiError.conflict("Online room availability changed. Please review the "
                    + "refreshed options before confirming.");
        }
    }

    /** {@code allocate_room_tx} — FOR UPDATE SKIP LOCKED. */
    public long allocateRoom(long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        List<Long> rows = jdbc.query(
                "SELECT r.id FROM rooms r WHERE r.room_type_id = ? AND r.is_active = true "
                        + "AND COALESCE(r.status, 'available') NOT IN ('maintenance', 'out_of_order') "
                        + "AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.room_id = r.id "
                        + "AND b.status IN (" + ACTIVE_BOOKING_STATUSES + ") "
                        + "AND b.check_in_date < ? AND b.check_out_date > ?) "
                        + "ORDER BY r.id FOR UPDATE SKIP LOCKED LIMIT 1",
                (rs, i) -> rs.getLong(1), roomTypeId, checkIn, checkOut);
        if (rows.isEmpty()) {
            throw ApiError.conflict("The selected room type was just booked");
        }
        return rows.get(0);
    }

    /**
     * {@code insert_anonymous_guest_tx}: fresh profile every time, name
     * uniqueness enforced under a SAVEPOINT so the violation converts to
     * {@link ApiError.Kind#GUEST_NAME_TAKEN} instead of aborting the tx.
     */
    public long insertAnonymousGuest(ValidatedAnonymousGuest details, String languagePreference) {
        jdbc.execute("SAVEPOINT anon_guest_name");
        try {
            Long guestId = jdbc.queryForObject("""
                    INSERT INTO guests (nick_name, first_name, last_name, email, phone,
                        tourism_type, language_preference)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, details.nickName(), details.firstName(), details.lastName(),
                    details.email(), details.phone(), details.tourismType(), languagePreference);
            jdbc.execute("RELEASE SAVEPOINT anon_guest_name");
            return guestId;
        } catch (Exception e) {
            try {
                jdbc.execute("ROLLBACK TO SAVEPOINT anon_guest_name");
            } catch (Exception ignored) {
                // The outer transaction rolls back anyway.
            }
            if (isGuestNameUniqueViolation(e)) {
                throw ApiError.guestNameTaken();
            }
            throw e;
        }
    }

    /** {@code is_guest_name_unique_violation} — SQLSTATE 23505 on idx_guests_nick_name_unique. */
    public static boolean isGuestNameUniqueViolation(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof DuplicateKeyException) {
                break;
            }
            cursor = cursor.getCause();
        }
        return cursor != null && String.valueOf(error.getMessage())
                .contains("idx_guests_nick_name_unique");
    }

    /** {@code insert_booking_tx} — credit-settled stays skip pending_payment. */
    public long insertBooking(BookingInsert input) {
        String status = input.settledByCredits() ? "confirmed" : "pending_payment";
        String paymentStatus = input.settledByCredits() ? "paid" : "unpaid";
        return jdbc.queryForObject("""
                INSERT INTO bookings (
                    portal_request_id, booking_number, guest_id, room_id,
                    check_in_date, check_out_date, adults, children,
                    room_rate, subtotal, tax_amount, discount_amount, total_amount,
                    currency, status, payment_status, source, booking_channel_id,
                    special_requests, cleaning_preference, daily_rates, created_by,
                    is_complimentary, complimentary_reason, is_tourist, tourism_tax_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 'website', ?,
                    ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                input.portalRequestId(), input.bookingNumber(), input.guestId(), input.roomId(),
                input.checkInDate(), input.checkOutDate(), input.adults(), input.children(),
                input.roomRate(), input.subtotal(), input.discountAmount(), input.totalAmount(),
                input.currency(), status, paymentStatus, input.bookingChannelId(),
                input.specialRequests(), input.cleaningPreference(),
                objectMapper.writeValueAsString(input.nightlyRates()), input.actorUserId(),
                input.complimentaryReason() != null, input.complimentaryReason(),
                input.isTourist(), input.tourismTaxAmount());
    }

    /** {@code issue_access_token_tx} — prefixed SHA-256 in pre_checkin_token. */
    public void issueAccessToken(long bookingId, String token, OffsetDateTime expiresAt) {
        jdbc.update("UPDATE bookings SET pre_checkin_token = ?, "
                        + "pre_checkin_token_expires_at = ? WHERE id = ?",
                PortalAuth.persistBookingAccessToken(token), expiresAt, bookingId);
    }

    /** {@code redeem_complimentary_credits_tx} — guarded decrement. */
    public void redeemComplimentaryCredits(long guestId, long roomTypeId, int nights) {
        int updated = jdbc.update("""
                UPDATE guest_complimentary_credits
                SET nights_available = nights_available - ?, updated_at = CURRENT_TIMESTAMP
                WHERE guest_id = ? AND room_type_id = ? AND nights_available >= ?
                """, nights, guestId, roomTypeId, nights);
        if (updated != 1) {
            throw ApiError.conflict(
                    "Your complimentary nights for this room type are no longer available");
        }
    }

    /** {@code redeem_voucher_tx}. */
    public void redeemVoucher(VoucherPricing voucher, long bookingId, long guestId,
            Long actorUserId, BigDecimal subtotal, BigDecimal discountAmount,
            BigDecimal totalAmount) {
        int updated = jdbc.update("""
                UPDATE vouchers SET status = 'redeemed', redeemed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND guest_id = ? AND status = 'available'
                """, voucher.voucherId(), guestId);
        if (updated != 1) {
            throw ApiError.conflict("This voucher was already used");
        }
        jdbc.update("""
                INSERT INTO voucher_redemptions (
                    voucher_id, promotion_id, booking_id, guest_id, status,
                    gross_subtotal, discount_type, discount_value,
                    discount_amount, net_total, applied_by)
                VALUES (?, ?, ?, ?, 'applied', ?, ?, ?, ?, ?, ?)
                """, voucher.voucherId(), voucher.promotionId(), bookingId, guestId, subtotal,
                voucher.discountType(), voucher.discountValue(), discountAmount, totalAmount,
                actorUserId);
    }

    /** {@code mark_room_reserved_tx}. */
    public void markRoomReserved(long roomId, String bookingNumber) {
        jdbc.update("""
                UPDATE rooms SET
                    status = CASE WHEN status IN ('dirty', 'cleaning', 'reserved_dirty')
                                  THEN 'reserved_dirty' ELSE 'reserved' END,
                    status_notes = ?
                WHERE id = ?
                """, "Website booking #" + bookingNumber, roomId);
    }

    /** {@code insert_delivery_tx} — idempotent on idempotency_key. */
    public void insertDelivery(Long campaignId, String kind, long guestId, String topic,
            String recipientEmail, String subject, String bodyHtml, String bodyText,
            Long voucherId, String idempotencyKey) {
        jdbc.update("""
                INSERT INTO email_deliveries
                    (campaign_id, kind, guest_id, topic, recipient_email, subject,
                     body_html, body_text, voucher_id, idempotency_key)
                VALUES (?, ?, ?, ?, LOWER(?), ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, campaignId, kind, guestId, topic, recipientEmail, subject, bodyHtml,
                bodyText, voucherId, idempotencyKey);
    }

    /** {@code upsert_online_inventory}. */
    @Transactional
    public void upsertOnlineInventory(long roomTypeId, LocalDate stayDate, int reserved,
            boolean enabled, BigDecimal customPrice, long updatedBy) {
        lockRoomType(roomTypeId);
        jdbc.update("""
                INSERT INTO online_inventory_allocations
                    (room_type_id, stay_date, walk_in_reserved_rooms, online_booking_enabled,
                     custom_price, updated_by)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (room_type_id, stay_date) DO UPDATE SET
                    walk_in_reserved_rooms = EXCLUDED.walk_in_reserved_rooms,
                    online_booking_enabled = EXCLUDED.online_booking_enabled,
                    custom_price = EXCLUDED.custom_price,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = CURRENT_TIMESTAMP
                """, roomTypeId, stayDate, reserved, enabled, customPrice, updatedBy);
    }

    // ------------------------------------------------------------------
    // Composed booking writes (service.rs::create / create_anonymous tx body)
    // ------------------------------------------------------------------

    /** Everything {@code create}'s transaction needs pre-computed by the service. */
    public record SessionBookingPlan(
            BookingInsert insert,
            long roomTypeId, LocalDate checkIn, LocalDate checkOut,
            VoucherPricing voucherToRedeem, int complimentaryNights,
            BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount,
            BigDecimal complimentaryDiscount,
            List<ConsentAcceptance> consents, String ipAddress, String userAgent,
            GuestContact contact, String bookingStatus,
            String mailSubject, String mailHtml, String mailText) {
    }

    /** Everything {@code create_anonymous}'s transaction needs pre-computed. */
    public record AnonymousBookingPlan(
            ValidatedAnonymousGuest guest, String languagePreference,
            String accessToken, OffsetDateTime accessTokenExpiresAt,
            BookingInsert insert,
            long roomTypeId, LocalDate checkIn, LocalDate checkOut,
            List<ConsentAcceptance> consents, String ipAddress, String userAgent,
            String bookingNumber, String recipientEmail,
            String mailSubject, String mailHtml, String mailText) {
    }

    /** {@code create}'s tx: allocate → insert → redeem → history → audit → consent → mail. */
    @Transactional
    public long persistSessionBooking(SessionBookingPlan plan) {
        ensureOnlineRoomAvailable(plan.roomTypeId(), plan.checkIn(), plan.checkOut());
        long roomId = allocateRoom(plan.roomTypeId(), plan.checkIn(), plan.checkOut());
        BookingInsert insert = new BookingInsert(
                plan.insert().portalRequestId(), plan.insert().guestId(),
                plan.insert().actorUserId(), roomId, plan.insert().bookingNumber(),
                plan.insert().checkInDate(), plan.insert().checkOutDate(),
                plan.insert().adults(), plan.insert().children(), plan.insert().roomRate(),
                plan.insert().subtotal(), plan.insert().discountAmount(),
                plan.insert().totalAmount(), plan.insert().currency(),
                plan.insert().specialRequests(), plan.insert().cleaningPreference(),
                plan.insert().bookingChannelId(), plan.insert().nightlyRates(),
                plan.insert().complimentaryReason(), plan.insert().settledByCredits(),
                plan.insert().isTourist(), plan.insert().tourismTaxAmount());
        long bookingId = insertBooking(insert);

        if (plan.complimentaryNights() > 0) {
            redeemComplimentaryCredits(plan.insert().guestId(), plan.roomTypeId(),
                    plan.complimentaryNights());
        }
        if (plan.voucherToRedeem() != null) {
            redeemVoucher(plan.voucherToRedeem(), bookingId, plan.insert().guestId(),
                    plan.contact().actorUserId(), plan.subtotal(), plan.discountAmount(),
                    plan.totalAmount());
        }
        markRoomReserved(roomId, plan.insert().bookingNumber());
        bookingOps.recordBookingHistory(bookingId, null, plan.bookingStatus(),
                plan.contact().actorUserId(),
                plan.insert().settledByCredits()
                        ? "Booking created in guest portal (settled with complimentary credits)"
                        : "Booking created in guest portal (pending payment)",
                Map.of("source", "website", "guest_id", plan.insert().guestId(),
                        "room_type_id", plan.roomTypeId(),
                        "portal_request_id", plan.insert().portalRequestId(),
                        "complimentary_nights", plan.complimentaryNights()));
        audit.event(plan.contact().actorUserId(), "guest_portal.booking_created", "booking",
                bookingId, Map.of(
                        "booking_number", plan.insert().bookingNumber(),
                        "room_type_id", plan.roomTypeId(),
                        "check_in_date", plan.checkIn().toString(),
                        "check_out_date", plan.checkOut().toString(),
                        "total_amount", plan.totalAmount().toPlainString(),
                        "currency", plan.insert().currency(),
                        "complimentary_nights", plan.complimentaryNights(),
                        "complimentary_discount", plan.complimentaryDiscount().toPlainString()),
                plan.ipAddress(), plan.userAgent());
        consents.record(Consents.Subject.guest(plan.insert().guestId()).withBooking(bookingId),
                plan.consents(), Consents.Source.ONLINE_BOOKING,
                new Consents.Context(plan.ipAddress(), plan.userAgent()));
        if (plan.mailSubject() != null && plan.contact().email() != null) {
            insertDelivery(null, "booking_confirmation", plan.insert().guestId(),
                    "booking_confirmation", plan.contact().email(), plan.mailSubject(),
                    plan.mailHtml(), plan.mailText(), null,
                    "booking-confirmation:" + bookingId);
        }
        return bookingId;
    }

    /** {@code create_anonymous}'s tx: guest → allocate → insert → token → history → consent → audit → mail. */
    @Transactional
    public long persistAnonymousBooking(AnonymousBookingPlan plan) {
        ensureOnlineRoomAvailable(plan.roomTypeId(), plan.checkIn(), plan.checkOut());
        long roomId = allocateRoom(plan.roomTypeId(), plan.checkIn(), plan.checkOut());
        long guestId = insertAnonymousGuest(plan.guest(), plan.languagePreference());
        BookingInsert insert = new BookingInsert(
                plan.insert().portalRequestId(), guestId, null, roomId, plan.bookingNumber(),
                plan.insert().checkInDate(), plan.insert().checkOutDate(),
                plan.insert().adults(), plan.insert().children(), plan.insert().roomRate(),
                plan.insert().subtotal(), plan.insert().discountAmount(),
                plan.insert().totalAmount(), plan.insert().currency(),
                plan.insert().specialRequests(), plan.insert().cleaningPreference(),
                plan.insert().bookingChannelId(), plan.insert().nightlyRates(),
                null, false, plan.insert().isTourist(), plan.insert().tourismTaxAmount());
        long bookingId = insertBooking(insert);
        issueAccessToken(bookingId, plan.accessToken(), plan.accessTokenExpiresAt());
        markRoomReserved(roomId, plan.bookingNumber());
        bookingOps.recordBookingHistory(bookingId, null, "pending_payment", null,
                "Booking created on the website without an account (pending payment)",
                Map.of("source", "website", "guest_id", guestId,
                        "room_type_id", plan.roomTypeId(),
                        "portal_request_id", plan.insert().portalRequestId(),
                        "anonymous", true));
        consents.record(Consents.Subject.anonymousBooking(guestId, bookingId),
                plan.consents(), Consents.Source.ONLINE_BOOKING,
                new Consents.Context(plan.ipAddress(), plan.userAgent()));
        audit.event(null, "guest_portal.anonymous_booking_created", "booking", bookingId,
                Map.of("booking_number", plan.bookingNumber(),
                        "room_type_id", plan.roomTypeId(),
                        "check_in_date", plan.checkIn().toString(),
                        "check_out_date", plan.checkOut().toString(),
                        "total_amount", plan.insert().totalAmount().toPlainString(),
                        "currency", plan.insert().currency(),
                        "guest_id", guestId),
                plan.ipAddress(), plan.userAgent());
        insertDelivery(null, "booking_confirmation", guestId, "booking_confirmation",
                plan.recipientEmail(), plan.mailSubject(), plan.mailHtml(), plan.mailText(),
                null, "booking-confirmation:" + bookingId);
        return bookingId;
    }

    public PortalBookingOps bookingOps() {
        return bookingOps;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }
}
