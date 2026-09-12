package com.hotelapp.bookings;

import com.hotelapp.billing.InvoiceNumbers;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.payments.PaymentRepo;
import com.hotelapp.portal.PortalBookingOps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Transactional half of {@code repositories::bookings::lifecycle::
 * update_booking_handler}: the booking UPDATE, deposit reconciliation,
 * status-transition side effects that are money-critical (payment voiding,
 * company-ledger auto-post, ledger delta sync), and the audit/history/
 * modification rows that must commit atomically with the booking.
 */
@Component
public class BookingLifecycleTx {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final PortalBookingOps bookings;
    private final PaymentRepo payments;
    private final InvoiceNumbers invoiceNumbers;
    private final ObjectMapper objectMapper;

    public BookingLifecycleTx(JdbcTemplate jdbc, AuditWriter audit,
            PortalBookingOps bookings, PaymentRepo payments,
            InvoiceNumbers invoiceNumbers, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.bookings = bookings;
        this.payments = payments;
        this.invoiceNumbers = invoiceNumbers;
        this.objectMapper = objectMapper;
    }

    /** The values {@link #updateBookingTx} needs but the caller computed. */
    public record UpdateContext(long bookingId, long userId, BookingUpdate input,
            Map<String, Object> existing, long newRoomId, String newStatus,
            LocalDate checkIn, LocalDate checkOut, String postType,
            String newPaymentStatus, Map<String, Object> dailyRatesJson,
            BigDecimal newRoomRate, BigDecimal newSubtotal, BigDecimal newTotalAmount,
            Boolean canonicalIsTourist, BigDecimal canonicalTourismTaxAmount,
            boolean clearCompany, String otaReference,
            LocalDateTime actualCheckOutOverride, long defaultTermsDays) {
    }

    /** {@code update_booking_handler}'s transaction body — returns nothing; the
     * service re-reads the booking post-commit for the response. */
    @Transactional
    public Map<String, Object> updateBookingTx(UpdateContext ctx) {
        BookingUpdate input = ctx.input();
        Map<String, Object> existing = ctx.existing();

        Map<String, Object> booking = jdbc.queryForObject("""
                UPDATE bookings SET
                    room_id = ?,
                    status = ?,
                    check_in_date = ?,
                    check_out_date = ?,
                    post_type = ?,
                    payment_status = ?,
                    deposit_paid = COALESCE(?::boolean, deposit_paid),
                    deposit_amount = COALESCE(?::numeric, deposit_amount),
                    deposit_paid_at = CASE WHEN ?::boolean = true AND deposit_paid_at IS NULL
                        THEN CURRENT_TIMESTAMP ELSE deposit_paid_at END,
                    company_id = CASE WHEN ? THEN NULL ELSE COALESCE(?, company_id) END,
                    company_name = CASE WHEN ? THEN NULL ELSE COALESCE(?, company_name) END,
                    payment_note = COALESCE(?, payment_note),
                    remarks = COALESCE(?, remarks),
                    source = COALESCE(?, source),
                    payment_method = ?,
                    room_rate = COALESCE(?, room_rate),
                    subtotal = COALESCE(?, subtotal),
                    total_amount = COALESCE(?, total_amount),
                    rate_override_weekday = COALESCE(?, rate_override_weekday),
                    rate_override_weekend = COALESCE(?, rate_override_weekend),
                    special_requests = COALESCE(?, special_requests),
                    is_tourist = ?,
                    tourism_tax_amount = ?,
                    extra_bed_count = COALESCE(?, extra_bed_count),
                    extra_bed_charge = COALESCE(?, extra_bed_charge),
                    daily_rates = COALESCE(?::jsonb, daily_rates),
                    cleaning_preference = COALESCE(?, cleaning_preference),
                    actual_check_out = COALESCE(?, CASE WHEN ? = 'checked_out'
                        AND actual_check_out IS NULL THEN CURRENT_TIMESTAMP
                        ELSE actual_check_out END),
                    booking_channel_id = COALESCE(?, booking_channel_id),
                    ota_reference = COALESCE(?, ota_reference),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                RETURNING *
                """, (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    var md = rs.getMetaData();
                    for (int c = 1; c <= md.getColumnCount(); c++) {
                        row.put(md.getColumnLabel(c), rs.getObject(c));
                    }
                    return row;
                },
                ctx.newRoomId(),
                ctx.newStatus(),
                ctx.checkIn(),
                ctx.checkOut(),
                ctx.postType(),
                ctx.newPaymentStatus(),
                null,
                null,
                null,
                ctx.clearCompany(),
                input.companyId(),
                ctx.clearCompany(),
                input.companyName(),
                input.paymentNote(),
                input.remarks(),
                input.source(),
                input.paymentMethod(),
                ctx.newRoomRate(),
                ctx.newSubtotal(),
                ctx.newTotalAmount(),
                input.roomRateOverride() == null ? null
                        : BigDecimal.valueOf(input.roomRateOverride()),
                input.roomRateOverride() == null ? null
                        : BigDecimal.valueOf(input.roomRateOverride()),
                input.specialRequests(),
                ctx.canonicalIsTourist(),
                ctx.canonicalTourismTaxAmount(),
                input.extraBedCount(),
                input.extraBedCharge() == null ? null
                        : Double.isFinite(input.extraBedCharge())
                                ? BigDecimal.valueOf(input.extraBedCharge())
                                : BigDecimal.ZERO,
                ctx.dailyRatesJson() == null ? null
                        : objectMapper.writeValueAsString(ctx.dailyRatesJson()),
                input.cleaningPreference(),
                ctx.actualCheckOutOverride(),
                ctx.newStatus(),
                input.bookingChannelId(),
                ctx.otaReference(),
                ctx.bookingId());

        reconcileBookingDeposit(ctx.bookingId(), input, ctx.userId());

        String oldStatus = String.valueOf(existing.get("status"));
        String updatedStatus = String.valueOf(booking.get("status"));

        if (!oldStatus.equals(updatedStatus)) {
            Map<String, Object> historyMeta = new LinkedHashMap<>();
            historyMeta.put("room_id", booking.get("room_id"));
            historyMeta.put("payment_status", booking.get("payment_status"));
            historyMeta.put("balance_affecting_total", String.valueOf(booking.get("total_amount")));
            bookings.recordBookingHistory(ctx.bookingId(), oldStatus, updatedStatus,
                    ctx.userId(), historyReason(input), historyMeta);

            switch (updatedStatus) {
                case "voided" -> {
                    voidBookingPayments(ctx.bookingId());
                    payments.recomputePaymentStatus(ctx.bookingId());
                }
                case "checked_out", "completed" -> {
                    String companyName = booking.get("company_name") == null ? null
                            : String.valueOf(booking.get("company_name"));
                    BigDecimal totalAmount = booking.get("total_amount") == null
                            ? BigDecimal.ZERO
                            : new BigDecimal(booking.get("total_amount").toString());
                    if (companyName != null && !companyName.trim().isEmpty()
                            && totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                        autoPostCompanyLedger(booking, companyName, ctx.checkIn(),
                                ctx.checkOut(), ctx.userId(), ctx.defaultTermsDays());
                    }
                }
                default -> {
                }
            }
        }

        if (ctx.newTotalAmount() != null) {
            BigDecimal oldTotal = existing.get("total_amount") == null ? BigDecimal.ZERO
                    : new BigDecimal(existing.get("total_amount").toString());
            BigDecimal delta = ctx.newTotalAmount().subtract(oldTotal);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                jdbc.update("""
                        UPDATE customer_ledgers SET amount = amount + ?
                        WHERE booking_id = ? AND status IN ('pending', 'partial')
                          AND post_type = 'room_charge'
                          AND amount + ? > 0
                          AND amount + ? >= paid_amount
                        """, delta, ctx.bookingId(), delta, delta);
            }
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("room_id", ctx.newRoomId() != ((Number) existing.get("room_id")).longValue()
                ? ctx.newRoomId() : null);
        changes.put("status", !oldStatus.equals(updatedStatus) ? ctx.newStatus() : null);
        changes.put("check_in_date", input.checkInDate());
        changes.put("check_out_date", input.checkOutDate());
        changes.put("payment_status", input.paymentStatus());
        audit.event(ctx.userId(), "booking_updated", "booking", ctx.bookingId(), changes);

        String modificationType = !oldStatus.equals(updatedStatus) ? "status_change"
                : ctx.newRoomRate() != null ? "rate_change"
                : input.checkInDate() != null || input.checkOutDate() != null ? "date_change"
                : ctx.newRoomId() != ((Number) existing.get("room_id")).longValue()
                        ? "room_change" : "general_update";
        jdbc.update("""
                INSERT INTO booking_modifications
                    (booking_id, modification_type, old_value, new_value,
                     price_adjustment, modified_by)
                VALUES (?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)
                """, ctx.bookingId(), modificationType,
                objectMapper.writeValueAsString(snapshot(existing)),
                objectMapper.writeValueAsString(snapshot(booking)),
                ctx.newTotalAmount() == null ? BigDecimal.ZERO
                        : ctx.newTotalAmount().subtract(
                                new BigDecimal(existing.get("total_amount").toString())),
                ctx.userId());
        return booking;
    }

    /** {@code reconcile_booking_deposit_tx}. */
    private void reconcileBookingDeposit(long bookingId, BookingUpdate input, long userId) {
        Boolean paid = input.depositPaid();
        Double amount = input.depositAmount();
        enum Intent { NONE, COLLECT, WAIVE }
        Intent intent;
        BigDecimal requested = null;
        if (Boolean.TRUE.equals(paid) && amount != null && amount > 0.0) {
            if (!Double.isFinite(amount)) {
                throw ApiError.badRequest("Invalid deposit amount");
            }
            intent = Intent.COLLECT;
            requested = BigDecimal.valueOf(amount);
        } else if (Boolean.FALSE.equals(paid) && amount != null
                || Boolean.TRUE.equals(paid) && amount != null) {
            intent = Intent.WAIVE;
        } else {
            intent = Intent.NONE;
        }
        if (intent == Intent.NONE) {
            return;
        }

        List<Long> locked = jdbc.query(
                "SELECT id FROM bookings WHERE id = ? FOR UPDATE",
                (rs, i) -> rs.getLong(1), bookingId);
        if (locked.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }

        BigDecimal recorded = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE booking_id = ?"
                        + " AND payment_type = 'deposit' AND status = 'completed'",
                BigDecimal.class, bookingId);

        switch (intent) {
            case COLLECT -> {
                if (requested.compareTo(recorded) < 0) {
                    throw ApiError.badRequest("Recorded deposit payments total " + recorded
                            + " — lower it via a refund or payment void, not a booking edit");
                }
                BigDecimal delta = requested.subtract(recorded);
                if (delta.compareTo(BigDecimal.ZERO) > 0) {
                    jdbc.update("""
                            INSERT INTO payments
                                (uuid, booking_id, amount, payment_method, payment_type,
                                 status, notes, created_by)
                            VALUES (gen_uuidv7(), ?, ?, ?, 'deposit', 'completed', ?, ?)
                            """, bookingId, delta,
                            input.paymentMethod() == null ? "Cash" : input.paymentMethod(),
                            input.paymentNote() == null ? "Keycard deposit"
                                    : input.paymentNote(),
                            userId);
                }
                payments.syncBookingDepositMirror(bookingId);
            }
            case WAIVE -> {
                if (recorded.compareTo(BigDecimal.ZERO) > 0) {
                    throw ApiError.badRequest("A deposit payment of " + recorded
                            + " is recorded on this booking — refund or void the payment"
                            + " instead of clearing the flag");
                }
                jdbc.update("""
                        UPDATE bookings SET deposit_paid = false, deposit_amount = NULL,
                            deposit_paid_at = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, bookingId);
            }
            default -> {
            }
        }
    }

    /** {@code void_booking_payments_tx}. */
    private void voidBookingPayments(long bookingId) {
        jdbc.update("UPDATE payments SET status = 'void'"
                + " WHERE booking_id = ? AND status != 'void'", bookingId);
        payments.syncBookingDepositMirror(bookingId);
    }

    /** {@code auto_post_company_ledger}. */
    private void autoPostCompanyLedger(Map<String, Object> booking, String companyName,
            LocalDate checkIn, LocalDate checkOut, long userId, long defaultTermsDays) {
        long bookingId = ((Number) booking.get("id")).longValue();
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM customer_ledgers WHERE booking_id = ?"
                        + " AND post_type = 'room_charge'"
                        + " AND COALESCE(is_reversal, false) = false)",
                Boolean.class, bookingId);
        if (Boolean.TRUE.equals(exists)) {
            return;
        }

        long nights = Math.max(checkOut.toEpochDay() - checkIn.toEpochDay(), 1);
        List<Map<String, Object>> detail = jdbc.queryForList("""
                SELECT r.room_number, g.nick_name FROM bookings b
                LEFT JOIN rooms r ON b.room_id = r.id
                LEFT JOIN guests g ON b.guest_id = g.id WHERE b.id = ?
                """, bookingId);
        String roomNumber = detail.isEmpty() || detail.get(0).get("room_number") == null ? ""
                : String.valueOf(detail.get(0).get("room_number"));
        String guestName = detail.isEmpty() || detail.get(0).get("nick_name") == null ? ""
                : String.valueOf(detail.get(0).get("nick_name"));
        String description = "Room " + roomNumber + " - " + guestName + " (" + nights
                + " night" + (nights > 1 ? "s" : "") + ": " + checkIn + " to " + checkOut + ")";

        List<Long> termsRows = jdbc.queryForList(
                "SELECT payment_terms_days FROM companies WHERE company_name = ? LIMIT 1",
                Long.class, companyName);
        long terms = termsRows.isEmpty() || termsRows.get(0) == null
                ? defaultTermsDays : termsRows.get(0);
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);
        LocalDate dueDate = today.plusDays(terms);

        String invoiceNumber = jdbc.query(
                "SELECT invoice_number FROM invoices WHERE booking_id = ?"
                        + " AND invoice_number IS NOT NULL ORDER BY created_at LIMIT 1",
                (rs, i) -> rs.getString(1), bookingId).stream().findFirst().orElse(null);
        if (invoiceNumber == null) {
            // Serialize INV-YYYYMM allocation across concurrent checkouts — the
            // customer_ledgers invoice_number is UNIQUE and MAX+1 allocation
            // would otherwise race (upstream F5 comment).
            String yyyymm = jdbc.queryForObject(
                    "SELECT TO_CHAR(CURRENT_DATE, 'YYYYMM')", String.class);
            jdbc.update("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    "invoice_number:" + yyyymm);
            invoiceNumber = invoiceNumbers.nextInvoiceNumber();
        }

        jdbc.update("""
                INSERT INTO customer_ledgers (
                    company_name, description, expense_type, amount,
                    booking_id, post_type, posting_date, transaction_date,
                    invoice_date, due_date, room_number,
                    folio_type, transaction_type,
                    created_by, updated_by, cashier_id,
                    invoice_number
                )
                VALUES (?, ?, 'accommodation', ?,
                        ?, 'room_charge', CURRENT_DATE, CURRENT_DATE,
                        CURRENT_DATE, ?, ?,
                        'city_ledger', 'debit',
                        ?, ?, ?,
                        ?)
                """, companyName, description, booking.get("total_amount"),
                bookingId, dueDate, roomNumber, userId, userId, userId, invoiceNumber);
    }

    private static String historyReason(BookingUpdate input) {
        return input.remarks() != null ? input.remarks() : input.paymentNote();
    }

    private static Map<String, Object> snapshot(Map<String, Object> booking) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("status", str(booking.get("status")));
        snap.put("room_id", booking.get("room_id"));
        snap.put("room_rate", str(booking.get("room_rate")));
        snap.put("check_in_date", str(booking.get("check_in_date")));
        snap.put("check_out_date", str(booking.get("check_out_date")));
        snap.put("payment_status", str(booking.get("payment_status")));
        snap.put("total_amount", str(booking.get("total_amount")));
        return snap;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
