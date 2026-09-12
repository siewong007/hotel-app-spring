package com.hotelapp.payments;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code models::payment} — staff-side payment rows, request DTOs and
 * the review-queue shapes. Field names mirror the upstream serde contract.
 */
public final class PaymentModels {

    private PaymentModels() {
    }

    /** {@code PaymentEntryRow} — canonical staff payment row. */
    public record PaymentEntryRow(
            long id,
            @JsonProperty("booking_id") long bookingId,
            @JsonProperty("total_amount") String totalAmount,
            @JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("payment_type") String paymentType,
            @JsonProperty("payment_status") String paymentStatus,
            @JsonProperty("transaction_reference") String transactionReference,
            String notes,
            @JsonProperty("payment_date") String paymentDate,
            @JsonProperty("created_at") Object createdAt,
            @JsonProperty("idempotency_fingerprint") String idempotencyFingerprint) {

        /** {@code into_response} — the shape every payment row serializes to. */
        public Map<String, Object> intoResponse() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", id);
            body.put("booking_id", bookingId);
            body.put("total_amount", totalAmount);
            body.put("payment_method", paymentMethod);
            body.put("payment_type", paymentType);
            body.put("payment_status", paymentStatus);
            body.put("transaction_reference", transactionReference);
            body.put("notes", notes);
            body.put("payment_date", paymentDate);
            body.put("created_at", createdAt);
            return body;
        }
    }

    /** {@code RecordedPayment}. */
    public record RecordedPayment(PaymentEntryRow row, boolean wasInserted) {
    }

    /** {@code RecordPaymentRequest}. */
    public record RecordPaymentRequest(
            @JsonProperty("booking_id") Long bookingId,
            Double amount,
            @JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("payment_type") String paymentType,
            @JsonProperty("transaction_reference") String transactionReference,
            String notes,
            @JsonProperty("payment_date") String paymentDate,
            @JsonProperty("idempotency_key") String idempotencyKey) {
    }

    /** {@code UpdatePaymentRequest}. */
    public record UpdatePaymentRequest(
            Double amount,
            @JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("transaction_reference") String transactionReference,
            String notes,
            @JsonProperty("payment_date") String paymentDate) {
    }

    /** {@code RejectPaymentRequest}. */
    public record RejectPaymentRequest(String reason) {
    }

    /** {@code RequestPaymentReceiptRequest}. */
    public record RequestPaymentReceiptRequest(String message) {
    }

    /** {@code PendingPaymentEntry} — staff approval-queue row. */
    public record PendingPaymentEntry(
            long id,
            @JsonProperty("booking_id") long bookingId,
            @JsonProperty("booking_number") String bookingNumber,
            @JsonProperty("guest_id") Long guestId,
            @JsonProperty("guest_name") String guestName,
            String amount,
            @JsonProperty("payment_method") String paymentMethod,
            String status,
            String reference,
            String notes,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("receipt_requested") boolean receiptRequested,
            @JsonProperty("receipt_uploaded") boolean receiptUploaded,
            @JsonProperty("receipt_file_available") boolean receiptFileAvailable,
            @JsonProperty("processed_at") String processedAt,
            @JsonProperty("processed_by_name") String processedByName,
            @JsonProperty("decision_reason") String decisionReason) {
    }

    /** {@code PendingPaymentPage}. */
    public record PendingPaymentPage(
            List<PendingPaymentEntry> items,
            long total) {
    }

    /** {@code PaymentSummary} — the `payments/calculate/{id}` response. */
    public record PaymentSummary(
            BigDecimal subtotal,
            @JsonProperty("service_charge") BigDecimal serviceCharge,
            @JsonProperty("service_charge_percentage") BigDecimal serviceChargePercentage,
            @JsonProperty("tax_amount") BigDecimal taxAmount,
            @JsonProperty("tax_percentage") BigDecimal taxPercentage,
            @JsonProperty("keycard_deposit") BigDecimal keycardDeposit,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("payment_method") String paymentMethod) {
    }

    /** {@code Payment} — the `POST /payments` response. */
    public record Payment(
            long id,
            @JsonProperty("booking_id") long bookingId,
            @JsonProperty("user_id") Long userId,
            @JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("payment_status") String paymentStatus,
            BigDecimal subtotal,
            @JsonProperty("service_charge") BigDecimal serviceCharge,
            @JsonProperty("tax_amount") BigDecimal taxAmount,
            @JsonProperty("keycard_deposit") BigDecimal keycardDeposit,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("transaction_reference") String transactionReference,
            @JsonProperty("payment_gateway") String paymentGateway,
            @JsonProperty("card_last_four") String cardLastFour,
            @JsonProperty("card_brand") String cardBrand,
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("account_reference") String accountReference,
            String notes,
            @JsonProperty("created_at") Object createdAt,
            @JsonIgnore String idempotencyKey,
            @JsonIgnore String idempotencyFingerprint) {
    }

    /** {@code PaymentRequest} — the `POST /payments` body. */
    public record PaymentRequest(
            @JsonProperty("booking_id") Long bookingId,
            @JsonProperty("payment_method") String paymentMethod,
            Double amount,
            @JsonProperty("transaction_reference") String transactionReference,
            @JsonProperty("card_last_four") String cardLastFour,
            @JsonProperty("card_brand") String cardBrand,
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("account_reference") String accountReference,
            String notes,
            @JsonProperty("idempotency_key") String idempotencyKey) {
    }

    /** {@code CompletedPayment}. */
    public record CompletedPayment(Payment payment, boolean wasInserted) {
    }

    /** {@code PaymentWorkflowSummaryRow} — the booking-level money position. */
    public record WorkflowSummaryRow(
            String bookingStatus,
            String paymentStatus,
            BigDecimal totalAmount,
            BigDecimal tourismTaxAmount,
            BigDecimal extraBedCharge,
            BigDecimal totalPaid,
            BigDecimal totalRefunded,
            BigDecimal depositCollected,
            BigDecimal depositRefunded,
            boolean hasFailedPayment) {

        /** {@code billable_total} — room + tourism tax + extra bed. */
        public BigDecimal billableTotal() {
            return totalAmount.add(tourismTaxAmount).add(extraBedCharge);
        }
    }

    /** {@code PaymentWorkflowSummary} — the workflow-summary response. */
    public record PaymentWorkflowSummary(
            @JsonProperty("booking_id") long bookingId,
            @JsonProperty("booking_status") String bookingStatus,
            @JsonProperty("payment_status") String paymentStatus,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("total_paid") BigDecimal totalPaid,
            @JsonProperty("total_refunded") BigDecimal totalRefunded,
            @JsonProperty("balance_due") BigDecimal balanceDue,
            @JsonProperty("deposit_collected") BigDecimal depositCollected,
            @JsonProperty("deposit_refunded") BigDecimal depositRefunded,
            @JsonProperty("has_failed_payment") boolean hasFailedPayment,
            @JsonProperty("next_action") String nextAction,
            List<String> warnings) {
    }

    /** {@code PaymentReceiptFile} — a stored receipt's location + type. */
    public record PaymentReceiptFile(String path, String contentType) {
    }
}
