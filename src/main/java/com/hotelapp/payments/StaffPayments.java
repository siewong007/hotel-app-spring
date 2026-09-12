package com.hotelapp.payments;

import com.hotelapp.core.AfterCommit;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.RbacService;
import com.hotelapp.email.BookingEmails;
import com.hotelapp.loyalty.LoyaltyAwards;
import com.hotelapp.payments.PaymentModels.Payment;
import com.hotelapp.payments.PaymentModels.PaymentEntryRow;
import com.hotelapp.payments.PaymentModels.PaymentReceiptFile;
import com.hotelapp.payments.PaymentModels.PaymentRequest;
import com.hotelapp.payments.PaymentModels.PaymentSummary;
import com.hotelapp.payments.PaymentModels.PaymentWorkflowSummary;
import com.hotelapp.payments.PaymentModels.PendingPaymentEntry;
import com.hotelapp.payments.PaymentModels.PendingPaymentPage;
import com.hotelapp.payments.PaymentModels.RecordPaymentRequest;
import com.hotelapp.payments.PaymentModels.UpdatePaymentRequest;
import com.hotelapp.payments.PaymentModels.WorkflowSummaryRow;
import com.hotelapp.payments.StaffPaymentsTx.CreateOutcome;
import com.hotelapp.payments.StaffPaymentsTx.RecordOutcome;
import com.hotelapp.payments.StaffPaymentsTx.UpdateOutcome;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import com.hotelapp.portal.PortalPaymentTx;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of {@code services::payments} — the staff-side payment workflows
 * (record/update/void, approval queue, deposit refunds, sweeps). Guest-side
 * portal payments stay in {@code portal::PortalPayments}.
 */
@Component
public class StaffPayments {

    private static final Logger log = LoggerFactory.getLogger(StaffPayments.class);

    private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.005");
    static final String PAYMENT_RECEIPT_UPLOAD_DIR = "uploads/payment-receipts";

    private final JdbcTemplate jdbc;
    private final PaymentRepo repo;
    private final StaffPaymentsTx tx;
    private final PortalPaymentTx portalTx;
    private final AuditWriter audit;
    private final BookingEmails bookingEmails;
    private final LoyaltyAwards loyaltyAwards;
    private final RbacService rbac;

    public StaffPayments(JdbcTemplate jdbc, PaymentRepo repo, StaffPaymentsTx tx,
            PortalPaymentTx portalTx, AuditWriter audit, BookingEmails bookingEmails,
            LoyaltyAwards loyaltyAwards, RbacService rbac) {
        this.jdbc = jdbc;
        this.repo = repo;
        this.tx = tx;
        this.portalTx = portalTx;
        this.audit = audit;
        this.bookingEmails = bookingEmails;
        this.loyaltyAwards = loyaltyAwards;
        this.rbac = rbac;
    }

    /** {@code normalized_idempotency_key}. */
    public static String normalizedIdempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.isEmpty() || key.length() > 160) {
            throw ApiError.badRequest(
                    "Idempotency key must be between 1 and 160 characters");
        }
        return key;
    }

    /** {@code record_payment}. */
    public Map<String, Object> recordPayment(long userId, RecordPaymentRequest request) {
        if (request.bookingId() == null || request.amount() == null) {
            throw ApiError.badRequest("Booking ID and amount are required");
        }
        String idempotencyKey = normalizedIdempotencyKey(request.idempotencyKey());
        BigDecimal amount = BigDecimal.valueOf(request.amount());

        // Refund markers belong to the deposit-refund workflow; a caller-
        // selected type here would let `payments:create` forge a disbursement.
        String paymentType = request.paymentType() == null ? "booking" : request.paymentType();
        if (!List.of("booking", "deposit", "service", "damage").contains(paymentType)) {
            throw ApiError.badRequest("Unsupported payment type");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiError.badRequest("Payment amount must be positive");
        }

        RecordPaymentRequest normalized = new RecordPaymentRequest(
                request.bookingId(), request.amount(), request.paymentMethod(), paymentType,
                request.transactionReference(), request.notes(), request.paymentDate(),
                idempotencyKey);
        String fingerprint = PaymentRepo.canonicalPaymentFingerprint(
                request.bookingId(), amount, request.paymentMethod(), paymentType,
                request.transactionReference(), request.notes(), request.paymentDate());

        RecordOutcome outcome = tx.recordPaymentTx(userId, normalized, amount,
                paymentType, fingerprint);
        if (!outcome.wasInserted()) {
            return outcome.row().intoResponse();
        }
        PaymentEntryRow row = outcome.row();
        long bookingId = row.bookingId();
        long paymentId = row.id();
        boolean confirmedByThisPayment = outcome.confirmedByThisPayment();

        AfterCommit.run(() -> {
            boolean roomAssignmentNotified =
                    bookingEmails.tryQueuePaidOnlineBookingRoomAssignment(bookingId);
            if (confirmedByThisPayment && !roomAssignmentNotified) {
                bookingEmails.tryQueuePaymentConfirmationEmail(bookingId, paymentId);
            }
            loyaltyAwards.tryAwardEligibleBookingPoints(bookingId, paymentId, userId);
            audit.event(userId, "payment_recorded", "payment", paymentId,
                    Map.of("booking_id", bookingId, "amount", request.amount()));
        });
        return row.intoResponse();
    }

    /** {@code calculate_payment_summary}. */
    public PaymentSummary calculatePaymentSummary(long bookingId) {
        List<Map<String, Object>> stays = jdbc.queryForList(
                "SELECT room_id, check_in_date, check_out_date FROM bookings WHERE id = ?",
                bookingId);
        if (stays.isEmpty()) {
            throw ApiError.notFound("Resource not found");
        }
        Map<String, Object> stay = stays.get(0);
        List<Map<String, Object>> pricing = jdbc.queryForList("""
                SELECT rt.base_price, rt.keycard_deposit_amount, rt.service_charge_percentage
                FROM rooms r
                JOIN room_types rt ON r.room_type_id = rt.id
                WHERE r.id = ?
                """, ((Number) stay.get("room_id")).longValue());
        if (pricing.isEmpty()) {
            throw ApiError.notFound("Resource not found");
        }
        Map<String, Object> prices = pricing.get(0);
        java.time.LocalDate checkIn = (java.time.LocalDate) stay.get("check_in_date");
        java.time.LocalDate checkOut = (java.time.LocalDate) stay.get("check_out_date");
        long nights = java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        BigDecimal basePrice = (BigDecimal) prices.get("base_price");
        BigDecimal subtotal = basePrice.multiply(BigDecimal.valueOf(nights));
        BigDecimal serviceChargePercentage =
                (BigDecimal) prices.get("service_charge_percentage");
        BigDecimal serviceCharge = subtotal.multiply(serviceChargePercentage)
                .divide(BigDecimal.valueOf(100));
        BigDecimal keycardDeposit = (BigDecimal) prices.get("keycard_deposit_amount");
        return new PaymentSummary(
                subtotal,
                serviceCharge,
                serviceChargePercentage,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                keycardDeposit,
                subtotal.add(serviceCharge).add(keycardDeposit),
                null);
    }

    /** {@code create_payment}. */
    public Payment createPayment(long userId, PaymentRequest request) {
        if (request.bookingId() == null) {
            throw ApiError.badRequest("Booking ID is required");
        }
        String idempotencyKey = normalizedIdempotencyKey(request.idempotencyKey());
        PaymentSummary summary = calculatePaymentSummary(request.bookingId());
        String paymentGateway = switch (request.paymentMethod() == null
                ? "" : request.paymentMethod()) {
            case "card" -> "card_processor";
            case "duitnow" -> "duitnow";
            case "online_banking" -> "online_banking";
            default -> null;
        };
        String fingerprint = PaymentRepo.canonicalPaymentFingerprint(
                request.bookingId(),
                request.amount() == null ? null : BigDecimal.valueOf(request.amount()),
                request.paymentMethod(), "booking", request.transactionReference(),
                request.notes(), null);
        PaymentRequest normalized = new PaymentRequest(request.bookingId(),
                request.paymentMethod(), request.amount(), request.transactionReference(),
                request.cardLastFour(), request.cardBrand(), request.bankName(),
                request.accountReference(), request.notes(), idempotencyKey);

        CreateOutcome outcome = tx.createCompletedPaymentTx(userId, normalized, summary,
                paymentGateway, fingerprint);
        Payment payment = outcome.payment();
        if (!outcome.wasInserted()) {
            return payment;
        }
        long bookingId = payment.bookingId();
        long paymentId = payment.id();
        AfterCommit.run(() -> {
            bookingEmails.tryQueuePaidOnlineBookingRoomAssignment(bookingId);
            loyaltyAwards.tryAwardEligibleBookingPoints(bookingId, paymentId, userId);
            audit.event(userId, "payment_created", "payment", paymentId,
                    Map.of("booking_id", bookingId, "amount", payment.totalAmount()));
        });
        return payment;
    }

    /** {@code get_all_payments}. */
    public List<Map<String, Object>> getAllPayments(long bookingId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (PaymentEntryRow row : repo.listPaymentEntries(bookingId)) {
            out.add(row.intoResponse());
        }
        return out;
    }

    /** {@code get_payment} — the newest payment row for a booking, or null. */
    public Payment getPayment(long bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, booking_id, processed_by, created_by, payment_method, status, amount,
                       transaction_id, payment_gateway, card_last_four, card_brand, notes,
                       created_at, idempotency_key, idempotency_fingerprint
                FROM payments WHERE booking_id = ? ORDER BY created_at DESC LIMIT 1
                """, bookingId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Number processedBy = (Number) row.get("processed_by");
        Number createdBy = (Number) row.get("created_by");
        return new Payment(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("booking_id")).longValue(),
                processedBy != null ? processedBy.longValue()
                        : (createdBy != null ? createdBy.longValue() : null),
                String.valueOf(row.get("payment_method")),
                String.valueOf(row.get("status")),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                (BigDecimal) row.get("amount"),
                (String) row.get("transaction_id"),
                (String) row.get("payment_gateway"),
                (String) row.get("card_last_four"),
                (String) row.get("card_brand"),
                null,
                null,
                (String) row.get("notes"),
                row.get("created_at"),
                (String) row.get("idempotency_key"),
                (String) row.get("idempotency_fingerprint"));
    }

    /** {@code get_payment_workflow_summary}. */
    public PaymentWorkflowSummary getPaymentWorkflowSummary(long bookingId) {
        WorkflowSummaryRow row = repo.workflowSummaryRow(bookingId);
        if (row == null) {
            throw ApiError.notFound("Booking not found");
        }
        BigDecimal billableTotal = row.billableTotal();
        BigDecimal balanceDue = billableTotal.compareTo(row.totalPaid()) > 0
                ? billableTotal.subtract(row.totalPaid()) : BigDecimal.ZERO;

        List<String> warnings = new ArrayList<>();
        if (row.hasFailedPayment()) {
            warnings.add("One or more payments failed and need review");
        }
        if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            warnings.add("Outstanding balance: " + balanceDue);
        }
        boolean checkedOut = "checked_out".equals(row.bookingStatus())
                || "completed".equals(row.bookingStatus());
        if (row.depositCollected().compareTo(row.depositRefunded()) > 0 && checkedOut) {
            warnings.add("Collected deposit has not been fully refunded");
        }
        if (row.totalRefunded().compareTo(row.totalPaid()) > 0) {
            warnings.add("Refund total is greater than collected payments");
        }

        String nextAction;
        if ("voided".equals(row.bookingStatus())) {
            nextAction = "No payment action - booking is voided";
        } else if (row.hasFailedPayment()) {
            nextAction = "Review failed payment";
        } else if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            nextAction = "Collect balance due";
        } else if (row.depositCollected().compareTo(row.depositRefunded()) > 0 && checkedOut) {
            nextAction = "Refund deposit";
        } else {
            nextAction = "Settled";
        }

        return new PaymentWorkflowSummary(
                bookingId,
                row.bookingStatus(),
                row.paymentStatus(),
                billableTotal,
                row.totalPaid(),
                row.totalRefunded(),
                balanceDue,
                row.depositCollected(),
                row.depositRefunded(),
                row.hasFailedPayment(),
                nextAction,
                warnings);
    }

    /** {@code update_payment}. */
    public Map<String, Object> updatePayment(long userId, long paymentId,
            UpdatePaymentRequest request) {
        long bookingId = repo.paymentBookingId(paymentId);
        UpdateOutcome outcome = tx.updatePaymentTx(bookingId, paymentId, request);
        PaymentEntryRow outcomeBefore = outcome.before();
        PaymentEntryRow row = outcome.row();

        AfterCommit.run(() -> {
            bookingEmails.tryQueuePaidOnlineBookingRoomAssignment(row.bookingId());
            loyaltyAwards.tryAwardEligibleBookingPoints(row.bookingId(), row.id(), null);
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("amount", outcomeBefore.totalAmount());
            before.put("payment_method", outcomeBefore.paymentMethod());
            before.put("payment_type", outcomeBefore.paymentType());
            before.put("status", outcomeBefore.paymentStatus());
            before.put("transaction_reference", outcomeBefore.transactionReference());
            before.put("notes", outcomeBefore.notes());
            before.put("payment_date", outcomeBefore.paymentDate());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("amount", row.totalAmount());
            after.put("payment_method", row.paymentMethod());
            after.put("payment_type", row.paymentType());
            after.put("status", row.paymentStatus());
            after.put("transaction_reference", row.transactionReference());
            after.put("notes", row.notes());
            after.put("payment_date", row.paymentDate());
            audit.event(userId, "payment_updated", "payment", row.id(), Map.of(
                    "booking_id", row.bookingId(),
                    "before", before,
                    "after", after));
        });
        return row.intoResponse();
    }

    /** {@code delete_payment} — a void, never a hard delete. */
    public Map<String, Object> deletePayment(long userId, long paymentId) {
        long bookingId = repo.paymentBookingId(paymentId);
        boolean canVoidCompleted = rbac.hasPermission(userId, "payments:manage");
        PaymentEntryRow voided = tx.deletePaymentTx(bookingId, paymentId, userId,
                canVoidCompleted);
        Map<String, Object> voidedPayment = new LinkedHashMap<>();
        voidedPayment.put("amount", voided.totalAmount());
        voidedPayment.put("payment_method", voided.paymentMethod());
        voidedPayment.put("payment_type", voided.paymentType());
        voidedPayment.put("status", voided.paymentStatus());
        voidedPayment.put("transaction_reference", voided.transactionReference());
        voidedPayment.put("notes", voided.notes());
        voidedPayment.put("payment_date", voided.paymentDate());
        audit.event(userId, "payment_voided", "payment", paymentId, Map.of(
                "booking_id", bookingId,
                "voided_payment", voidedPayment));
        return Map.of(
                "success", true,
                "message", "Payment voided",
                "deleted_id", paymentId,
                "voided_id", paymentId);
    }

    /** {@code refund_deposit}. */
    public Map<String, Object> refundDeposit(long userId, long bookingId,
            Map<String, Object> body) {
        String paymentMethod = body == null ? "cash"
                : String.valueOf(body.getOrDefault("payment_method", "cash"));
        Number amountNumber = body == null ? null : (Number) body.get("amount");
        BigDecimal depositAmount = amountNumber == null ? BigDecimal.ZERO
                : new BigDecimal(amountNumber.toString());
        if (depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiError.badRequest("No deposit amount provided");
        }
        PaymentEntryRow row = tx.refundDepositTx(userId, bookingId, paymentMethod,
                depositAmount);
        repo.recomputePaymentStatus(bookingId);
        audit.event(userId, "payment_refunded", "payment", row.id(), Map.of(
                "booking_id", bookingId,
                "amount", depositAmount,
                "payment_method", paymentMethod));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("id", row.id());
        out.put("booking_id", row.bookingId());
        out.put("total_amount", row.totalAmount());
        out.put("payment_method", row.paymentMethod());
        out.put("payment_type", row.paymentType());
        out.put("payment_status", row.paymentStatus());
        out.put("notes", row.notes());
        out.put("created_at", row.createdAt());
        return out;
    }

    /** {@code revert_deposit_refund}. */
    public Map<String, Object> revertDepositRefund(long userId, long bookingId) {
        long revertedPaymentId = tx.revertDepositRefundTx(bookingId);
        repo.recomputePaymentStatus(bookingId);
        audit.event(userId, "payment_refund_reverted", "payment", revertedPaymentId,
                Map.of("booking_id", bookingId));
        return Map.of(
                "booking_id", bookingId,
                "reverted_payment_id", revertedPaymentId,
                "deposit_refunded", false);
    }

    /** {@code list_pending_payments}. */
    public PendingPaymentPage listPendingPayments(long limit, long offset) {
        long boundedLimit = Math.max(1, Math.min(limit, 200));
        long boundedOffset = Math.max(0, offset);
        PaymentRepo.PendingEntryPage page =
                repo.listPendingPayments(boundedLimit, boundedOffset);
        return new PendingPaymentPage(page.items(), page.total());
    }

    /** {@code list_payment_approval_history}. */
    public PendingPaymentPage listPaymentApprovalHistory(long limit, long offset) {
        PaymentRepo.PendingEntryPage page = repo.listPaymentApprovalHistory(
                Math.max(1, Math.min(limit, 200)), Math.max(0, offset));
        return new PendingPaymentPage(page.items(), page.total());
    }

    /** {@code approve_payment}. */
    public PaymentActionResponse approvePayment(long userId, long paymentId) {
        PendingPaymentEntry review = repo.getPaymentForReview(paymentId);
        if (review == null) {
            throw ApiError.notFound("Payment not found.");
        }
        if (!"pending".equals(review.status())) {
            throw ApiError.badRequest("Only pending payments can be approved.");
        }
        if ("paypal".equals(review.paymentMethod())) {
            throw ApiError.badRequest(
                    "PayPal payments cannot be approved manually because their capture has "
                            + "not been verified.");
        }
        PaymentActionResponse response = portalTx.completeAndConfirm(
                paymentId, review.bookingId(), userId, "payment_approved");
        AfterCommit.run(() -> bookingEmails.tryQueuePaymentConfirmationEmail(
                review.bookingId(), paymentId));
        return response;
    }

    /** {@code reject_payment}. */
    public PaymentActionResponse rejectPayment(long userId, long paymentId, String reason) {
        return rejectPaymentBy(userId, paymentId, reason);
    }

    /**
     * {@code reject_payment_by} — also the sweep path (`actorUserId == null`
     * means the automated decision is not attributed to a staff account).
     */
    PaymentActionResponse rejectPaymentBy(Long actorUserId, long paymentId, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) {
            throw ApiError.badRequest("A rejection reason is required.");
        }
        PendingPaymentEntry review = repo.getPaymentForReview(paymentId);
        if (review == null) {
            throw ApiError.notFound("Payment not found.");
        }
        if (!"pending".equals(review.status())) {
            throw ApiError.badRequest("Only pending payments can be rejected.");
        }

        boolean resetToPayment = tx.rejectPaymentTx(actorUserId, paymentId,
                review.bookingId(), trimmed);

        AfterCommit.run(() -> bookingEmails.tryQueuePaymentRejectedNotification(
                review.guestId(), review.guestName(), review.bookingId(),
                review.bookingNumber(), paymentId, trimmed));

        return new PaymentActionResponse(paymentId, "void",
                resetToPayment ? "pending_payment" : null);
    }

    /** {@code request_payment_receipt}. */
    public void requestPaymentReceipt(long userId, long paymentId, String message) {
        PendingPaymentEntry review = repo.getPaymentForReview(paymentId);
        if (review == null) {
            throw ApiError.notFound("Payment not found.");
        }
        String trimmed = message == null ? null
                : (message.trim().isEmpty() ? null : message.trim());
        if (!"pending".equals(review.status())
                || !"bank_transfer".equals(review.paymentMethod())) {
            throw ApiError.badRequest(
                    "A receipt can only be requested for a pending bank-transfer claim.");
        }
        repo.requestReceipt(paymentId, userId, trimmed);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("booking_id", review.bookingId());
        auditDetails.put("message", trimmed);
        audit.event(userId, "payment_receipt_requested", "payment", paymentId, auditDetails);
        bookingEmails.queuePaymentReceiptRequestNotification(review.guestId(),
                review.guestName(), review.bookingId(), review.bookingNumber(),
                paymentId, trimmed);
    }

    /** {@code load_payment_receipt}. */
    public ReceiptPayload loadPaymentReceipt(long paymentId) {
        PaymentReceiptFile receipt = repo.receiptFile(paymentId);
        if (receipt == null) {
            throw ApiError.notFound("No receipt has been uploaded for this payment.");
        }
        Path root;
        Path path;
        try {
            root = Path.of(PAYMENT_RECEIPT_UPLOAD_DIR).toRealPath();
            path = Path.of(receipt.path()).toRealPath();
        } catch (Exception e) {
            throw ApiError.notFound("Receipt file is unavailable.");
        }
        if (!path.startsWith(root)) {
            throw ApiError.notFound("Receipt file is unavailable.");
        }
        try {
            return new ReceiptPayload(Files.readAllBytes(path), receipt.contentType());
        } catch (Exception e) {
            throw ApiError.notFound("Receipt file is unavailable.");
        }
    }

    /** (bytes, contentType) for the receipt download response. */
    public record ReceiptPayload(byte[] bytes, String contentType) {
    }

    /** {@code reject_expired_receipt_requests}. */
    public int rejectExpiredReceiptRequests() {
        int rejected = 0;
        for (long paymentId : repo.expiredReceiptRequestPaymentIds()) {
            try {
                rejectPaymentBy(null, paymentId,
                        "Receipt was not uploaded within 24 hours of the request.");
                rejected++;
            } catch (ApiError ignored) {
            }
        }
        return rejected;
    }

    /**
     * {@code reject_expired_paypal_attempts} — release orders never passed to
     * capture within ten minutes. A capture claims its row as `processing`
     * before calling PayPal, so the sweep only sees attempts for which no
     * money-movement request began.
     */
    public int rejectExpiredPaypalAttempts() {
        int rejected = 0;
        for (long paymentId : repo.expiredPaypalAttemptIds()) {
            try {
                rejectPaymentBy(null, paymentId,
                        "PayPal payment was not completed within 10 minutes. "
                                + "Please start a new payment attempt.");
                rejected++;
            } catch (ApiError ignored) {
            }
        }
        return rejected;
    }
}
