package com.hotelapp.billing;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.AfterCommit;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.email.BookingEmails;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final BookingEmails bookingEmails;

    public BillingController(JdbcTemplate jdbc, AuditWriter audit,
            BookingEmails bookingEmails) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.bookingEmails = bookingEmails;
    }

    @GetMapping("/api/payments/calculate/{bookingId}")
    public Map<String, Object> calculate(@PathVariable long bookingId) {
        requireBooking(bookingId);
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT COALESCE(total_amount, 0) AS total_amount,
                       COALESCE(tax_amount, 0) AS tax_amount,
                       COALESCE(discount_amount, 0) AS discount_amount,
                       COALESCE(deposit_amount, 0) AS deposit_amount,
                       status, payment_status
                FROM bookings WHERE id = ?
                """, bookingId);
        BigDecimal paid = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM payments "
                        + "WHERE booking_id = ? AND status IN ('completed','confirmed')",
                BigDecimal.class, bookingId));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("booking_id", bookingId);
        body.putAll(totals);
        body.put("paid_amount", paid);
        body.put("balance_due", dec(totals.get("total_amount")).subtract(paid));
        return body;
    }

    private long insertPayment(long userId, long bookingId, Map<String, Object> body) {
        requireBooking(bookingId);
        BigDecimal amount = numD(body.get("amount"));
        String receiptNumber = "RCP-" + System.currentTimeMillis();
        Long id = jdbc.queryForObject("""
                INSERT INTO payments (booking_id, amount, payment_method, payment_date,
                    reference_number, notes, status, recorded_by)
                VALUES (?, ?, COALESCE(?, 'cash'), CURRENT_DATE, ?, ?, 'completed', ?)
                RETURNING id
                """, Long.class, bookingId, amount, str(body, "payment_method"),
                receiptNumber, str(body, "reference_number"), str(body, "notes"), userId);
        return id;
    }

    private static long requiredBookingId(Map<String, Object> body) {
        Number bookingId = num(body, "booking_id");
        if (bookingId == null || numD(body.get("amount")) == null) {
            throw ApiError.badRequest("Booking ID and amount are required");
        }
        return bookingId.longValue();
    }

    /** {@code recompute_payment_status} — running paid/partial/unpaid position. */
    private void recomputePaymentStatus(long bookingId) {
        jdbc.update("""
                UPDATE bookings AS b
                SET payment_status = CASE
                    WHEN b.status = 'voided' THEN 'void'
                    WHEN COALESCE(b.is_complimentary, false)
                         THEN COALESCE(b.payment_status, 'paid')
                    WHEN b.total_amount <= 0 THEN 'paid'
                    WHEN COALESCE((SELECT SUM(p.amount) FROM payments p
                            WHERE p.booking_id = b.id
                              AND p.status = 'completed'
                              AND COALESCE(p.payment_type, 'booking') != 'refund'), 0)
                         >= b.total_amount THEN 'paid'
                    WHEN COALESCE((SELECT SUM(p.amount) FROM payments p
                            WHERE p.booking_id = b.id
                              AND p.status = 'completed'
                              AND COALESCE(p.payment_type, 'booking') != 'refund'), 0) > 0
                        THEN 'partial'
                    ELSE 'unpaid'
                END,
                updated_at = CURRENT_TIMESTAMP
                WHERE b.id = ?
                """, bookingId);
    }

    @PostMapping("/api/payments/record-payment")
    @Transactional
    public Map<String, Object> recordPayment(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        long bookingId = requiredBookingId(body);
        long paymentId = insertPayment(userId, bookingId, body);
        recomputePaymentStatus(bookingId);

        // A desk payment that settles the balance confirms a still-pending
        // booking — the same as an approved bank-transfer claim. Only the
        // payment that flips the booking mails the guest.
        String paymentStatus = jdbc.queryForObject(
                "SELECT payment_status FROM bookings WHERE id = ?",
                String.class, bookingId);
        boolean confirmedByThisPayment = false;
        if ("paid".equals(paymentStatus)) {
            confirmedByThisPayment = jdbc.update("""
                    UPDATE bookings SET status = 'confirmed', updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status IN ('pending','pending_payment','pending_confirmation')
                    """, bookingId) == 1;
            if (confirmedByThisPayment) {
                jdbc.update("""
                        INSERT INTO booking_history (booking_id, changed_field, old_value,
                            new_value, changed_by, notes)
                        VALUES (?, 'status', 'pending', 'confirmed', ?, ?)
                        """, bookingId, userId, "Payment recorded in full");
            }
        }
        audit.event(userId, "payment_recorded", "payment", paymentId,
                Map.of("amount", body.get("amount")));
        boolean confirmed = confirmedByThisPayment;
        AfterCommit.run(() -> {
            // A portal booking paid online already hears "payment confirmed"
            // from the room-assignment mail — suppress the duplicate.
            boolean roomAssignmentNotified =
                    bookingEmails.tryQueuePaidOnlineBookingRoomAssignment(bookingId);
            if (confirmed && !roomAssignmentNotified) {
                bookingEmails.tryQueuePaymentConfirmationEmail(bookingId, paymentId);
            }
        });
        return onePayment(paymentId);
    }

    @PostMapping("/api/payments")
    @Transactional
    public Map<String, Object> createPayment(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        long bookingId = requiredBookingId(body);
        long paymentId = insertPayment(userId, bookingId, body);
        recomputePaymentStatus(bookingId);
        audit.event(userId, "payment_created", "payment", paymentId,
                Map.of("booking_id", bookingId, "amount", body.get("amount")));
        AfterCommit.run(
                () -> bookingEmails.tryQueuePaidOnlineBookingRoomAssignment(bookingId));
        return onePayment(paymentId);
    }

    @GetMapping("/api/payments/booking/{bookingId}")
    public List<Map<String, Object>> bookingPayments(@PathVariable long bookingId) {
        return jdbc.queryForList("SELECT * FROM payments WHERE booking_id = ? "
                + "ORDER BY created_at", bookingId);
    }

    @GetMapping("/api/payments/all-payments/{bookingId}")
    public List<Map<String, Object>> allPayments(@PathVariable long bookingId) {
        return bookingPayments(bookingId);
    }

    @PatchMapping("/api/payments/{paymentId}")
    @Transactional
    public Map<String, Object> updatePayment(@PathVariable long paymentId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> payment = onePayment(paymentId);
        Number bookingIdNum = (Number) payment.get("booking_id");
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("amount", "payment_method", "reference_number",
                "notes", "status")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE payments SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
            audit.event(CurrentUser.require().userId(), "payment_updated", "payment",
                    paymentId, null);
            if (bookingIdNum != null) {
                long bookingId = bookingIdNum.longValue();
                recomputePaymentStatus(bookingId);
                AfterCommit.run(() -> bookingEmails
                        .tryQueuePaidOnlineBookingRoomAssignment(bookingId));
            }
        } else {
            audit.event(CurrentUser.require().userId(), "payment_updated", "payment",
                    paymentId, null);
        }
        return onePayment(paymentId);
    }

    @DeleteMapping("/api/payments/{paymentId}")
    public Map<String, Object> deletePayment(@PathVariable long paymentId) {
        long userId = CurrentUser.require().userId();
        onePayment(paymentId);
        jdbc.update("DELETE FROM payments WHERE id = ?", paymentId);
        audit.event(userId, "payment_deleted", "payment", paymentId, null);
        return message("Payment deleted successfully");
    }

    @PostMapping("/api/payments/refund-deposit/{bookingId}")
    public Map<String, Object> refundDeposit(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        requireBooking(bookingId);
        jdbc.update("UPDATE bookings SET deposit_paid = false WHERE id = ?", bookingId);
        audit.event(userId, "deposit_refunded", "booking", bookingId, null);
        return message("Deposit refund recorded successfully");
    }

    @PostMapping("/api/payments/revert-deposit-refund/{bookingId}")
    public Map<String, Object> revertDepositRefund(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        requireBooking(bookingId);
        jdbc.update("UPDATE bookings SET deposit_paid = true WHERE id = ?", bookingId);
        audit.event(userId, "deposit_refund_reverted", "booking", bookingId, null);
        return message("Deposit refund reverted successfully");
    }

    @GetMapping("/api/payments/workflow-summary/{bookingId}")
    public Map<String, Object> workflowSummary(@PathVariable long bookingId) {
        calculate(bookingId);
        Map<String, Object> summary = jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE is_complimentary) AS complimentary_count,
                       COUNT(*) AS payments_count
                FROM payments WHERE booking_id = ?
                """, bookingId);
        Map<String, Object> body = new LinkedHashMap<>(summary);
        body.put("booking", requireBooking(bookingId));
        return body;
    }

    @GetMapping("/api/invoices")
    public List<Map<String, Object>> listInvoices() {
        gate();
        return jdbc.queryForList("SELECT * FROM invoices ORDER BY issue_date DESC LIMIT 200");
    }

    @GetMapping("/api/invoices/preview/{bookingId}")
    public Map<String, Object> previewInvoice(@PathVariable long bookingId) {
        Map<String, Object> booking = requireBooking(bookingId);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("booking", booking);
        preview.put("charges", jdbc.queryForList(
                "SELECT * FROM customer_ledgers WHERE booking_id = ?", bookingId));
        preview.put("payments", jdbc.queryForList(
                "SELECT * FROM payments WHERE booking_id = ?", bookingId));
        return preview;
    }

    @PostMapping("/api/invoices/generate/{bookingId}")
    public Map<String, Object> generateInvoice(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        Map<String, Object> booking = requireBooking(bookingId);
        String invoiceNumber = "INV-" + bookingId + "-" + System.currentTimeMillis();
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO invoices (invoice_number, booking_id, invoice_type, issue_date,
                        billing_name, billing_email,
                        subtotal, tax_amount, total_amount, paid_amount, balance_due, currency, status)
                    SELECT ?, ?, 'standard', CURRENT_DATE,
                        CASE
                            WHEN NULLIF(BTRIM(g.first_name), '') IS NOT NULL
                             AND NULLIF(BTRIM(g.last_name), '') IS NOT NULL
                            THEN BTRIM(g.first_name) || ' ' || BTRIM(g.last_name)
                            ELSE COALESCE(BTRIM(g.nick_name), '')
                        END,
                        g.email,
                        ?, ?, ?, ?, ?, COALESCE(?, 'USD'), 'issued'
                    FROM bookings b
                    INNER JOIN guests g ON b.guest_id = g.id
                    WHERE b.id = ?
                    RETURNING id
                    """, Long.class, invoiceNumber, bookingId,
                    dec(booking.get("subtotal")), dec(booking.get("tax_amount")),
                    dec(booking.get("total_amount")),
                    nz(jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM payments "
                            + "WHERE booking_id = ? AND status IN ('completed','confirmed')",
                            BigDecimal.class, bookingId)),
                    dec(booking.get("total_amount")).subtract(dec(booking.get("subtotal"))),
                    str(booking, "currency"), bookingId);
            audit.event(userId, "invoice_generated", "invoice", id,
                    Map.of("invoice_number", invoiceNumber));
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM invoices WHERE id = ?", id);
            return rows.get(0);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiError.conflict("Invoice already exists for this booking");
        }
    }

    private void gate() {
        CurrentUser.require();
    }

    private Map<String, Object> requireBooking(long bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM bookings WHERE id = ?", bookingId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> onePayment(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM payments WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Payment not found");
        }
        return rows.get(0);
    }

    static BigDecimal dec(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    static BigDecimal numD(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            return new BigDecimal(s.trim());
        }
        return null;
    }
}
