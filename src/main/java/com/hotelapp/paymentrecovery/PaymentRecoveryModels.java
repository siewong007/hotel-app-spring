package com.hotelapp.paymentrecovery;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/** DTOs for the emailed payment-recovery surface (handlers/payment_retry.rs). */
public final class PaymentRecoveryModels {

    private PaymentRecoveryModels() {
    }

    /**
     * What the recovery page may show (upstream {@code PaymentRecoveryView}).
     * Deliberately minimal — no guest name, email, address or stay detail:
     * anyone holding the emailed link can see this.
     */
    public record PaymentRecoveryView(
            @JsonProperty("booking_number") String bookingNumber,
            @JsonProperty("amount_due") String amountDue,
            String currency,
            @JsonProperty("expires_at") OffsetDateTime expiresAt,
            @JsonProperty("payment_methods") List<String> paymentMethods,
            @JsonProperty("paypal_client_id") String paypalClientId,
            @JsonProperty("payment_id") Long paymentId,
            @JsonProperty("receipt_uploadable") boolean receiptUploadable,
            @JsonProperty("already_submitted") boolean alreadySubmitted) {
    }

    /** {@code CaptureRecoveredPaypalRequest}. */
    public record CaptureRecoveredPaypalRequest(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("payment_id") long paymentId) {
    }

    /** Row of {@code payment_retry_capabilities} (upstream {@code PaymentRetryCapability}). */
    public record Capability(
            long id,
            long bookingId,
            Long paymentId,
            OffsetDateTime expiresAt,
            OffsetDateTime consumedAt,
            Long replacementPaymentId) {

        /** {@code is_consumed}. */
        public boolean isConsumed() {
            return consumedAt != null;
        }

        /** {@code is_spendable_at(now)} — live and unexpired. */
        public boolean isSpendableAt(OffsetDateTime now) {
            return !isConsumed() && expiresAt.isAfter(now);
        }
    }
}
