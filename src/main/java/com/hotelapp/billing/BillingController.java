package com.hotelapp.billing;

import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.payments.PaymentModels.Payment;
import com.hotelapp.payments.PaymentModels.PaymentRequest;
import com.hotelapp.payments.PaymentModels.PaymentSummary;
import com.hotelapp.payments.PaymentModels.PaymentWorkflowSummary;
import com.hotelapp.payments.PaymentModels.RecordPaymentRequest;
import com.hotelapp.payments.PaymentModels.UpdatePaymentRequest;
import com.hotelapp.payments.StaffPayments;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final StaffPayments payments;

    public BillingController(JdbcTemplate jdbc, AuditWriter audit,
            StaffPayments payments) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.payments = payments;
    }

    @GetMapping("/api/payments/calculate/{bookingId}")
    public PaymentSummary calculate(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:read");
        return payments.calculatePaymentSummary(bookingId);
    }

    @PostMapping("/api/payments/record-payment")
    public Map<String, Object> recordPayment(@RequestBody RecordPaymentRequest body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:create");
        return payments.recordPayment(userId, body);
    }

    @PostMapping("/api/payments")
    public Payment createPayment(@RequestBody PaymentRequest body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:create");
        return payments.createPayment(userId, body);
    }

    /** {@code get_payment} — newest payment row for the booking, or null. */
    @GetMapping("/api/payments/booking/{bookingId}")
    public Payment bookingPayment(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:read");
        return payments.getPayment(bookingId);
    }

    @GetMapping("/api/payments/all-payments/{bookingId}")
    public List<Map<String, Object>> allPayments(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:read");
        return payments.getAllPayments(bookingId);
    }

    @PatchMapping("/api/payments/{paymentId}")
    public Map<String, Object> updatePayment(@PathVariable long paymentId,
            @RequestBody UpdatePaymentRequest body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:update");
        return payments.updatePayment(userId, paymentId, body);
    }

    @DeleteMapping("/api/payments/{paymentId}")
    public Map<String, Object> deletePayment(@PathVariable long paymentId) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:delete");
        return payments.deletePayment(userId, paymentId);
    }

    @PostMapping("/api/payments/refund-deposit/{bookingId}")
    public Map<String, Object> refundDeposit(@PathVariable long bookingId,
            @RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:refund");
        return payments.refundDeposit(userId, bookingId, body);
    }

    @PostMapping("/api/payments/revert-deposit-refund/{bookingId}")
    public Map<String, Object> revertDepositRefund(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:manage");
        return payments.revertDepositRefund(userId, bookingId);
    }

    @GetMapping("/api/payments/workflow-summary/{bookingId}")
    public PaymentWorkflowSummary workflowSummary(@PathVariable long bookingId) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:read");
        return payments.getPaymentWorkflowSummary(bookingId);
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

    static BigDecimal dec(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
