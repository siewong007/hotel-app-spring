package com.hotelapp.portal;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.portal.PortalModels.GuestBankDetails;
import com.hotelapp.portal.PortalModels.GuestPaymentConfig;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import com.hotelapp.portal.PortalModels.PaypalCreateOrderResponse;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of the guest-facing payment orchestration in {@code services/payments.rs}:
 * bank-transfer claims, PayPal order/capture, receipt storage, payment-config.
 *
 * Transaction-scoped writes live in {@link PortalPaymentTx} so this bean can
 * call PayPal between committed transactions exactly as upstream does
 * ({@code tx.commit()} before contacting the gateway).
 */
@Component
public class PortalPayments {

    private static final Logger log = LoggerFactory.getLogger(PortalPayments.class);

    public static final String PAYMENT_RECEIPT_UPLOAD_DIR = "private_uploads/payment_receipts";
    public static final long MAX_PAYMENT_RECEIPT_BYTES = 10L * 1024 * 1024;
    private static final String DEFAULT_CURRENCY = "MYR";

    private final JdbcTemplate jdbc;
    private final PortalPaymentTx tx;
    private final PaypalClient paypal;
    private final AuditWriter audit;
    private final AppProperties props;

    public PortalPayments(JdbcTemplate jdbc, PortalPaymentTx tx, PaypalClient paypal,
            AuditWriter audit, AppProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.paypal = paypal;
        this.audit = audit;
        this.props = props;
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    /** {@code guest_payment_config} — public payment panel config. */
    public GuestPaymentConfig guestPaymentConfig() {
        return new GuestPaymentConfig(
                paypal.isEnabled(),
                props.paypalPublicClientId(),
                new GuestBankDetails(props.getHotelBankName(), props.getHotelBankAccountName(),
                        props.getHotelBankAccountNumber()));
    }

    static String bookingCurrency(Map<String, Object> booking) {
        Object currency = booking.get("currency");
        return currency == null || currency.toString().trim().isEmpty()
                ? DEFAULT_CURRENCY : currency.toString();
    }

    /** Payment row for review — the fields the portal flows consult. */
    public Map<String, Object> getPaymentForReview(long paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.booking_id, b.guest_id AS guest_id, p.amount::text AS amount,
                       p.payment_method, p.status,
                       p.gateway_payment_intent_id AS reference, p.notes AS notes
                FROM payments p
                JOIN bookings b ON b.id = p.booking_id
                WHERE p.id = ?
                """, paymentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ------------------------------------------------------------------
    // Bank transfer
    // ------------------------------------------------------------------

    /** {@code create_bank_transfer_claim}. */
    public PaymentActionResponse createBankTransferClaim(Map<String, Object> booking) {
        return tx.createBankTransferClaim(booking);
    }

    /** {@code create_bank_transfer_claim_for_capability}. */
    public PaymentActionResponse createBankTransferClaim(Map<String, Object> booking,
            Long capabilityId) {
        return tx.createBankTransferClaim(booking, capabilityId);
    }

    // ------------------------------------------------------------------
    // PayPal
    // ------------------------------------------------------------------

    /** {@code create_paypal_order}. */
    public PaypalCreateOrderResponse createPaypalOrder(Map<String, Object> booking) {
        return createPaypalOrder(booking, null);
    }

    /**
     * {@code create_paypal_order_for_capability} — same business rules as the
     * portal path; the capability is spent inside the pending-payment
     * transaction. PayPal is contacted only after that transaction commits, so
     * a PayPal refusal releases the payment AND restores the capability —
     * otherwise the guest would hold a spent link that bought nothing.
     */
    public PaypalCreateOrderResponse createPaypalOrder(Map<String, Object> booking,
            Long capabilityId) {
        long bookingId = ((Number) booking.get("id")).longValue();
        long paymentId = tx.insertPendingPaypalPayment(booking, capabilityId);

        String customId = bookingId + ":" + paymentId;
        String orderId;
        try {
            orderId = paypal.createOrder((BigDecimal) booking.get("total_amount"),
                    bookingCurrency(booking), customId);
        } catch (ApiError error) {
            releaseFailedPaypalPayment(paymentId,
                    "PayPal could not create an order. No payment was captured.");
            if (capabilityId != null) {
                // Best effort: the guest already has an error, and failing here
                // too would replace it with a less useful one.
                try {
                    tx.restoreCapability(capabilityId, paymentId);
                } catch (Exception restoreError) {
                    log.error("Failed to restore payment retry capability {} "
                            + "after a PayPal order failure: {}",
                            capabilityId, restoreError.toString());
                }
            }
            throw error;
        }

        jdbc.update("UPDATE payments SET gateway_payment_intent_id = ? WHERE id = ?",
                orderId, paymentId);
        return new PaypalCreateOrderResponse(orderId, paymentId);
    }

    /** {@code capture_paypal_payment}. */
    public PaymentActionResponse capturePaypalPayment(Map<String, Object> booking,
            String orderId, long paymentId) {
        long bookingId = ((Number) booking.get("id")).longValue();
        Map<String, Object> review = getPaymentForReview(paymentId);
        if (review == null) {
            throw ApiError.notFound("Payment not found.");
        }
        if (((Number) review.get("booking_id")).longValue() != bookingId) {
            throw ApiError.forbidden("Payment does not belong to this booking.");
        }
        String reviewStatus = String.valueOf(review.get("status"));
        if ("completed".equals(reviewStatus)) {
            return new PaymentActionResponse(paymentId, "completed", "confirmed");
        }
        if (!"pending".equals(reviewStatus) && !"processing".equals(reviewStatus)) {
            throw ApiError.badRequest("This PayPal payment is no longer available for capture.");
        }
        if ("pending".equals(reviewStatus)) {
            int claimed = jdbc.update(
                    "UPDATE payments SET status = 'processing' WHERE id = ? AND status = 'pending'",
                    paymentId);
            if (claimed != 1) {
                throw ApiError.badRequest(
                        "This PayPal payment is no longer available for capture.");
            }
        }

        PaypalClient.PaypalCaptureOutcome outcome = paypal.captureOrder(orderId);
        if (!"COMPLETED".equals(outcome.status())) {
            releaseFailedPaypalPayment(paymentId,
                    "PayPal returned " + outcome.status() + " before completing the payment.");
            throw ApiError.badRequest(
                    "PayPal payment was not completed (status: " + outcome.status() + ").");
        }

        String expectedCustomId = bookingId + ":" + paymentId;
        if (outcome.customId() == null || !outcome.customId().equals(expectedCustomId)) {
            log.error("PayPal capture custom_id mismatch: expected {}, got {}",
                    expectedCustomId, outcome.customId());
            releaseFailedPaypalPayment(paymentId,
                    "PayPal order did not match the booking payment.");
            throw ApiError.badRequest("PayPal order does not match this booking.");
        }

        String mismatch = verifyCapturedAgainstStored(outcome,
                String.valueOf(review.get("amount")), bookingCurrency(booking));
        if (mismatch != null) {
            log.error("PayPal capture amount/currency mismatch for payment {}: {}",
                    paymentId, mismatch);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("source", "paypal_capture");
            details.put("order_id", orderId);
            details.put("booking_id", bookingId);
            details.put("reason", mismatch);
            audit.event(null, "paypal_capture_conflict", "payment", paymentId, details);
            throw ApiError.conflict("The captured PayPal payment does not match this booking's "
                    + "payment record. It has been flagged for hotel staff to review — "
                    + "please do not pay again.");
        }

        try {
            return tx.completeAndConfirm(paymentId, bookingId, null, "payment_captured");
        } catch (ApiError error) {
            if (error.kind() != ApiError.Kind.BAD_REQUEST) {
                throw error;
            }
            Map<String, Object> now = getPaymentForReview(paymentId);
            if (now != null && "completed".equals(String.valueOf(now.get("status")))) {
                return new PaymentActionResponse(paymentId, "completed", "confirmed");
            }
            throw ApiError.badRequest("This PayPal payment is no longer available for capture.");
        }
    }

    /** {@code find_gateway_order_id} — the PayPal order id, if created yet. */
    public String findGatewayOrderId(long paymentId) {
        List<String> rows = jdbc.query(
                "SELECT gateway_payment_intent_id FROM payments WHERE id = ?",
                (rs, i) -> rs.getString(1), paymentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code release_failed_paypal_payment} — CAS pending/processing -> failed. */
    boolean releaseFailedPaypalPayment(long paymentId, String reason) {
        return jdbc.update("""
                UPDATE payments SET status = 'failed', failure_reason = ?,
                    processed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('pending', 'processing')
                """, reason, paymentId) == 1;
    }

    /** {@code verify_captured_against_stored} — numeric amount + currency check. */
    private String verifyCapturedAgainstStored(PaypalClient.PaypalCaptureOutcome outcome,
            String storedAmount, String expectedCurrency) {
        if (outcome.capturedAmount() == null) {
            return "PayPal capture response did not include an amount.";
        }
        if (outcome.capturedCurrency() == null) {
            return "PayPal capture response did not include a currency.";
        }
        BigDecimal expected;
        try {
            expected = new BigDecimal(storedAmount.trim());
        } catch (NumberFormatException e) {
            return "Stored payment amount is unparseable.";
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(outcome.capturedAmount().trim());
        } catch (NumberFormatException e) {
            return "Unparseable captured amount '" + outcome.capturedAmount() + "'.";
        }
        if (parsed.compareTo(expected) != 0) {
            return "Captured amount " + parsed + " does not match expected " + expected + ".";
        }
        if (!outcome.capturedCurrency().equalsIgnoreCase(expectedCurrency)) {
            return "Captured currency " + outcome.capturedCurrency()
                    + " does not match expected " + expectedCurrency + ".";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Receipt storage
    // ------------------------------------------------------------------

    /** {@code receipt_extension} — validates actual file signatures. */
    static String[] receiptExtension(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return new String[] {"jpg", "image/jpeg"};
        }
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                && bytes[3] == 'G' && bytes[4] == '\r' && bytes[5] == '\n'
                && bytes[6] == 0x1a && bytes[7] == '\n') {
            return new String[] {"png", "image/png"};
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return new String[] {"webp", "image/webp"};
        }
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D'
                && bytes[3] == 'F' && bytes[4] == '-') {
            return new String[] {"pdf", "application/pdf"};
        }
        return null;
    }

    /** {@code save_payment_receipt}. */
    public void savePaymentReceipt(long paymentId, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_PAYMENT_RECEIPT_BYTES) {
            throw ApiError.badRequest("Receipt file size must be between 1 byte and 10MB");
        }
        Map<String, Object> receipt = getPaymentForReview(paymentId);
        if (receipt == null) {
            throw ApiError.notFound("Payment not found.");
        }
        if (!"bank_transfer".equals(String.valueOf(receipt.get("payment_method")))
                || !"pending".equals(String.valueOf(receipt.get("status")))) {
            throw ApiError.badRequest(
                    "A receipt can only be uploaded for a pending bank-transfer claim.");
        }
        String[] extension = receiptExtension(bytes);
        if (extension == null) {
            throw ApiError.badRequest("Receipt must be a JPEG, PNG, WebP, or PDF file.");
        }
        Path directory = Path.of(PAYMENT_RECEIPT_UPLOAD_DIR);
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw ApiError.internal("Unable to prepare receipt storage.");
        }
        Path path = directory.resolve(
                "payment_" + paymentId + "_" + UUID.randomUUID() + "." + extension[0]);
        try {
            Files.write(path, bytes);
        } catch (Exception e) {
            throw ApiError.internal("Unable to save the receipt.");
        }
        try {
            jdbc.update("""
                    INSERT INTO payment_receipt_requests
                        (payment_id, uploaded_at, receipt_path, receipt_content_type)
                    VALUES (?, CURRENT_TIMESTAMP, ?, ?)
                    ON CONFLICT (payment_id) DO UPDATE SET
                        uploaded_at = CURRENT_TIMESTAMP,
                        receipt_path = EXCLUDED.receipt_path,
                        receipt_content_type = EXCLUDED.receipt_content_type
                    """, paymentId, path.toString(), extension[1]);
        } catch (Exception error) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
            throw error instanceof ApiError apiError ? apiError
                    : ApiError.internal("Unable to save the receipt.");
        }
        audit.event(null, "payment_receipt_uploaded", "payment", paymentId,
                Map.of("booking_id", receipt.get("booking_id")));
    }
}
