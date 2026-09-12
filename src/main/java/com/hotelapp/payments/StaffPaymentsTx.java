package com.hotelapp.payments;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.payments.PaymentModels.PaymentEntryRow;
import com.hotelapp.payments.PaymentModels.RecordPaymentRequest;
import com.hotelapp.payments.PaymentModels.UpdatePaymentRequest;
import com.hotelapp.payments.PaymentModels.WorkflowSummaryRow;
import com.hotelapp.portal.PortalBookingOps;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional half of {@code services::payments} + {@code repositories::payment}
 * — every method maps one upstream tx body.
 */
@Component
public class StaffPaymentsTx {

    private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.005");

    private final JdbcTemplate jdbc;
    private final PaymentRepo repo;
    private final PortalBookingOps bookings;
    private final AuditWriter audit;

    public StaffPaymentsTx(JdbcTemplate jdbc, PaymentRepo repo,
            PortalBookingOps bookings, AuditWriter audit) {
        this.jdbc = jdbc;
        this.repo = repo;
        this.bookings = bookings;
        this.audit = audit;
    }

    /** {@code record_payment}'s transaction body. */
    @Transactional
    public RecordOutcome recordPaymentTx(long userId, RecordPaymentRequest request,
            BigDecimal amount, String paymentType, String fingerprint) {
        repo.lockBookingForPayment(request.bookingId());

        if (request.transactionReference() != null && !request.transactionReference().isEmpty()) {
            repo.lockTransactionReferences(List.of(request.transactionReference()));
            List<PaymentEntryRow> matches =
                    repo.listKeyedReferencePayments(request.transactionReference());
            if (matches.size() > 1) {
                throw ApiError.conflict(
                        "Transaction reference has multiple existing payment owners");
            }
            if (!matches.isEmpty()) {
                PaymentEntryRow existing = matches.get(0);
                if (fingerprint.equals(existing.idempotencyFingerprint())) {
                    return new RecordOutcome(existing, false, false);
                }
                throw repo.transactionReferenceConflict(
                        existing.bookingId(), request.bookingId());
            }
        }

        PaymentEntryRow existing = repo.findIdempotentPayment(
                request.bookingId(), request.idempotencyKey());
        if (existing != null) {
            if (fingerprint.equals(existing.idempotencyFingerprint())) {
                return new RecordOutcome(existing, false, false);
            }
            throw ApiError.conflict(
                    "Idempotency key was already used with different payment data");
        }

        boolean settlesBalanceInFull = false;
        if ("booking".equals(paymentType)) {
            WorkflowSummaryRow summary = repo.workflowSummaryRow(request.bookingId());
            if (summary != null) {
                BigDecimal billableTotal = summary.billableTotal();
                BigDecimal balanceDue = billableTotal.compareTo(summary.totalPaid()) > 0
                        ? billableTotal.subtract(summary.totalPaid())
                        : BigDecimal.ZERO;
                if (amount.compareTo(balanceDue.add(BALANCE_TOLERANCE)) > 0) {
                    throw ApiError.badRequest(
                            "Payment amount cannot exceed the outstanding balance of "
                                    + balanceDue);
                }
                settlesBalanceInFull =
                        amount.add(BALANCE_TOLERANCE).compareTo(balanceDue) >= 0;
            }
        }

        String createdAtOverride = request.paymentDate() == null
                ? null : request.paymentDate() + " 12:00:00";
        PaymentEntryRow row = insertPayment(userId, request, amount, paymentType,
                fingerprint, createdAtOverride);

        if ("deposit".equals(paymentType)) {
            repo.syncBookingDepositMirror(request.bookingId());
        }
        repo.recomputePaymentStatus(request.bookingId());

        boolean confirmedByThisPayment = false;
        if ("booking".equals(paymentType) && settlesBalanceInFull) {
            boolean confirmed = bookings.confirmBooking(request.bookingId());
            if (confirmed) {
                confirmedByThisPayment = true;
                bookings.recordBookingHistory(request.bookingId(), "pending", "confirmed",
                        userId, "Payment recorded in full",
                        Map.of("payment_id", row.id()));
            }
        }
        return new RecordOutcome(row, true, confirmedByThisPayment);
    }

    /** {@code RecordedPayment} + whether this payment flipped the booking. */
    public record RecordOutcome(PaymentEntryRow row, boolean wasInserted,
            boolean confirmedByThisPayment) {
    }

    /** {@code create_payment}'s transaction body ({@code create_completed_payment_tx}). */
    @Transactional
    public CreateOutcome createCompletedPaymentTx(long userId,
            PaymentModels.PaymentRequest request, PaymentModels.PaymentSummary summary,
            String paymentGateway, String fingerprint) {
        repo.lockBookingForPayment(request.bookingId());
        String paymentMethod = request.paymentMethod();

        if (request.transactionReference() != null && !request.transactionReference().isEmpty()) {
            repo.lockTransactionReferences(List.of(request.transactionReference()));
            List<PaymentEntryRow> matches =
                    repo.listKeyedReferencePayments(request.transactionReference());
            if (matches.size() > 1) {
                throw ApiError.conflict(
                        "Transaction reference has multiple existing payment owners");
            }
            if (!matches.isEmpty()) {
                PaymentEntryRow existing = matches.get(0);
                if (!fingerprint.equals(existing.idempotencyFingerprint())) {
                    throw repo.transactionReferenceConflict(
                            existing.bookingId(), request.bookingId());
                }
                return new CreateOutcome(replayPayment(existing.id(), userId, request,
                        summary, paymentMethod, paymentGateway, fingerprint), false);
            }
        }

        PaymentEntryRow existing = repo.findIdempotentPayment(
                request.bookingId(), request.idempotencyKey());
        if (existing != null) {
            if (!fingerprint.equals(existing.idempotencyFingerprint())) {
                throw ApiError.conflict(
                        "Idempotency key was already used with different payment data");
            }
            return new CreateOutcome(replayPayment(existing.id(), userId, request,
                    summary, paymentMethod, paymentGateway, fingerprint), false);
        }

        List<Long> existingCompleted = jdbc.query(
                "SELECT id FROM payments WHERE booking_id = ? AND status = 'completed' LIMIT 1",
                (rs, i) -> rs.getLong(1), request.bookingId());
        if (!existingCompleted.isEmpty()) {
            throw ApiError.badRequest(
                    "A completed payment already exists for this booking");
        }

        List<Map<String, Object>> inserted = jdbc.queryForList("""
                INSERT INTO payments (
                    uuid, booking_id, amount, payment_method, payment_type, status,
                    transaction_id, card_last_four, card_brand, payment_gateway, notes,
                    created_by, idempotency_key, idempotency_fingerprint
                )
                VALUES (gen_uuidv7(), ?, ?, ?, 'booking', 'completed', ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id, created_at
                """, request.bookingId(), summary.totalAmount(), paymentMethod,
                request.transactionReference(), request.cardLastFour(), request.cardBrand(),
                paymentGateway, request.notes(), userId, request.idempotencyKey(), fingerprint);
        Map<String, Object> row = inserted.get(0);
        PaymentModels.Payment payment = new PaymentModels.Payment(
                ((Number) row.get("id")).longValue(),
                request.bookingId(),
                userId,
                paymentMethod,
                "completed",
                summary.subtotal(),
                summary.serviceCharge(),
                summary.taxAmount(),
                summary.keycardDeposit(),
                summary.totalAmount(),
                request.transactionReference(),
                paymentGateway,
                request.cardLastFour(),
                request.cardBrand(),
                request.bankName(),
                request.accountReference(),
                request.notes(),
                row.get("created_at"),
                request.idempotencyKey(),
                fingerprint);
        repo.recomputePaymentStatus(request.bookingId());
        return new CreateOutcome(payment, true);
    }

    /** (payment, was_inserted) for {@code create_completed_payment_tx}. */
    public record CreateOutcome(PaymentModels.Payment payment, boolean wasInserted) {
    }

    /** The replayed `Payment` for a keyed/idempotent retry. */
    private PaymentModels.Payment replayPayment(long paymentId, long userId,
            PaymentModels.PaymentRequest request, PaymentModels.PaymentSummary summary,
            String paymentMethod, String paymentGateway, String fingerprint) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, booking_id, processed_by, created_by, payment_method, status, amount,
                       transaction_id, payment_gateway, card_last_four, card_brand, notes,
                       created_at, idempotency_key, idempotency_fingerprint
                FROM payments WHERE id = ?
                """, paymentId);
        Map<String, Object> row = rows.get(0);
        Number processedBy = (Number) row.get("processed_by");
        Number createdBy = (Number) row.get("created_by");
        return new PaymentModels.Payment(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("booking_id")).longValue(),
                processedBy != null ? processedBy.longValue()
                        : (createdBy != null ? createdBy.longValue() : null),
                String.valueOf(row.get("payment_method")),
                String.valueOf(row.get("status")),
                summary.subtotal(),
                summary.serviceCharge(),
                summary.taxAmount(),
                summary.keycardDeposit(),
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

    /** {@code insert_payment} — completed payment insert with fingerprint. */
    private PaymentEntryRow insertPayment(long userId, RecordPaymentRequest request,
            BigDecimal amount, String paymentType, String fingerprint,
            String createdAtOverride) {
        String insert = """
                INSERT INTO payments (
                    uuid, booking_id, amount, payment_method, payment_type,
                    status, transaction_id, notes, created_by, created_at,
                    idempotency_key, idempotency_fingerprint
                )
                VALUES (gen_uuidv7(), ?, ?, ?, ?, 'completed', ?, ?, ?, %s, ?, ?)
                RETURNING id, booking_id, amount::text AS total_amount, payment_method,
                          payment_type, status AS payment_status,
                          transaction_id AS transaction_reference, notes,
                          created_at::date::text AS payment_date, created_at,
                          idempotency_fingerprint
                """.formatted(createdAtOverride != null ? "?::timestamptz" : "CURRENT_TIMESTAMP");
        List<PaymentEntryRow> rows;
        if (createdAtOverride != null) {
            rows = jdbc.query(insert, PaymentRepo.PAYMENT_ENTRY_ROW,
                    request.bookingId(), amount, request.paymentMethod(), paymentType,
                    request.transactionReference(), request.notes(), userId,
                    createdAtOverride, request.idempotencyKey(), fingerprint);
        } else {
            rows = jdbc.query(insert, PaymentRepo.PAYMENT_ENTRY_ROW,
                    request.bookingId(), amount, request.paymentMethod(), paymentType,
                    request.transactionReference(), request.notes(), userId,
                    request.idempotencyKey(), fingerprint);
        }
        return rows.get(0);
    }

    /** {@code update_payment}'s transaction body — lock, update, recompute. */
    @Transactional
    public UpdateOutcome updatePaymentTx(long bookingId, long paymentId,
            UpdatePaymentRequest request) {
        repo.lockBookingForPayment(bookingId);
        UpdateOutcome outcome = applyPaymentUpdate(bookingId, paymentId, request);
        repo.recomputePaymentStatus(bookingId);
        return outcome;
    }

    /** {@code update_payment_tx}. */
    private UpdateOutcome applyPaymentUpdate(long bookingId, long paymentId,
            UpdatePaymentRequest request) {
        List<String> preliminaryRows = jdbc.query(
                "SELECT transaction_id FROM payments WHERE id = ? AND booking_id = ?",
                (rs, i) -> rs.getString(1), paymentId, bookingId);
        if (preliminaryRows.isEmpty()) {
            throw ApiError.notFound("Payment not found");
        }
        String oldTransactionReference = preliminaryRows.get(0);
        String finalTransactionReference = request.transactionReference() != null
                ? request.transactionReference() : oldTransactionReference;
        repo.lockTransactionReferences(List.of(
                oldTransactionReference == null ? "" : oldTransactionReference,
                finalTransactionReference == null ? "" : finalTransactionReference));

        List<PaymentEntryRow> existingRows = jdbc.query(
                "SELECT " + PaymentRepo.PAYMENT_ENTRY_COLS
                        + " FROM payments WHERE id = ? AND booking_id = ? FOR UPDATE",
                PaymentRepo.PAYMENT_ENTRY_ROW, paymentId, bookingId);
        if (existingRows.isEmpty()) {
            throw ApiError.notFound("Payment not found");
        }
        PaymentEntryRow existing = existingRows.get(0);

        String existingStatus = existing.paymentStatus() == null ? "" : existing.paymentStatus();
        String existingType = existing.paymentType() == null ? "booking" : existing.paymentType();
        if ("refund".equals(existingType)) {
            throw ApiError.badRequest(
                    "Refund records are immutable — use the refund revert workflow");
        }
        if ("refunded".equals(existingStatus) || "void".equals(existingStatus)) {
            throw ApiError.badRequest("A refunded or void payment cannot be modified");
        }

        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (request.amount() != null) {
            updates.add("amount = ?");
            params.add(BigDecimal.valueOf(request.amount()));
        }
        if (request.paymentMethod() != null) {
            updates.add("payment_method = ?");
            params.add(request.paymentMethod());
        }
        if (request.transactionReference() != null) {
            updates.add("transaction_id = ?");
            params.add(request.transactionReference());
        }
        if (request.notes() != null) {
            updates.add("notes = ?");
            params.add(request.notes());
        }
        if (request.paymentDate() != null) {
            updates.add("created_at = ?::timestamptz");
            params.add(request.paymentDate() + " 12:00:00");
        }
        if (updates.isEmpty()) {
            throw ApiError.badRequest("No fields to update");
        }

        BigDecimal existingAmount = parseAmount(existing.totalAmount());
        BigDecimal finalAmount = request.amount() != null
                ? BigDecimal.valueOf(request.amount()) : existingAmount;
        String finalPaymentMethod = request.paymentMethod() != null
                ? request.paymentMethod() : existing.paymentMethod();
        String finalPaymentType = existingType;
        String finalNotes = request.notes() != null ? request.notes() : existing.notes();

        String existingFingerprintWithoutRequestedDate =
                PaymentRepo.canonicalPaymentFingerprint(bookingId, existingAmount,
                        existing.paymentMethod(), existingType,
                        existing.transactionReference(), existing.notes(), null);
        String existingFingerprintWithRequestedDate =
                PaymentRepo.canonicalPaymentFingerprint(bookingId, existingAmount,
                        existing.paymentMethod(), existingType,
                        existing.transactionReference(), existing.notes(),
                        existing.paymentDate());
        String preservedRequestedDate = null;
        if (existingFingerprintWithoutRequestedDate.equals(existing.idempotencyFingerprint())) {
            preservedRequestedDate = null;
        } else if (existingFingerprintWithRequestedDate.equals(
                existing.idempotencyFingerprint())) {
            preservedRequestedDate = existing.paymentDate();
        }
        String finalPaymentDate = request.paymentDate() != null
                ? request.paymentDate() : preservedRequestedDate;

        if ("completed".equals(existingStatus)) {
            boolean amountChanged = request.amount() != null
                    && finalAmount.compareTo(existingAmount) != 0;
            boolean methodChanged = request.paymentMethod() != null
                    && !finalPaymentMethod.equals(existing.paymentMethod());
            boolean dateChanged = request.paymentDate() != null
                    && !java.util.Objects.equals(finalPaymentDate, existing.paymentDate());
            if (amountChanged || methodChanged || dateChanged) {
                throw ApiError.badRequest(
                        "Amount, method and payment date are immutable once a payment is "
                                + "posted — void the payment and record a new one instead");
            }
        }

        String fingerprint = PaymentRepo.canonicalPaymentFingerprint(bookingId, finalAmount,
                finalPaymentMethod, finalPaymentType, finalTransactionReference,
                finalNotes, finalPaymentDate);

        if (finalTransactionReference != null && !finalTransactionReference.isEmpty()) {
            Long ownerBookingId = repo.transactionReferenceOwnerBooking(
                    finalTransactionReference, paymentId);
            if (ownerBookingId != null) {
                throw repo.transactionReferenceConflict(ownerBookingId, bookingId);
            }
        }

        if (request.amount() != null) {
            if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiError.badRequest("Payment amount must be positive");
            }
            if ("booking".equals(finalPaymentType)) {
                WorkflowSummaryRow summary = repo.workflowSummaryRow(bookingId);
                if (summary != null) {
                    BigDecimal balance =
                            summary.billableTotal().subtract(summary.totalPaid());
                    if (finalAmount.compareTo(balance.add(BALANCE_TOLERANCE)) > 0) {
                        throw ApiError.badRequest(
                                "Payment amount cannot exceed the outstanding booking total "
                                        + "of " + balance);
                    }
                }
            }
        }

        updates.add("idempotency_fingerprint = ?");
        params.add(fingerprint);

        String updateSql = "UPDATE payments SET " + String.join(", ", updates)
                + " WHERE id = ? RETURNING " + PaymentRepo.PAYMENT_ENTRY_COLS;
        params.add(paymentId);
        PaymentEntryRow row = jdbc.query(updateSql, PaymentRepo.PAYMENT_ENTRY_ROW,
                params.toArray()).get(0);
        return new UpdateOutcome(existing, row);
    }

    /** (before, after) pair for the update audit. */
    public record UpdateOutcome(PaymentEntryRow before, PaymentEntryRow row) {
    }

    private static BigDecimal parseAmount(String totalAmount) {
        try {
            return new BigDecimal(totalAmount);
        } catch (NumberFormatException | NullPointerException e) {
            throw ApiError.database("Stored payment amount is invalid");
        }
    }

    /** {@code delete_payment}'s transaction body — lock, void, recompute. */
    @Transactional
    public PaymentEntryRow deletePaymentTx(long bookingId, long paymentId, long userId,
            boolean allowCompleted) {
        repo.lockBookingForPayment(bookingId);
        PaymentEntryRow voided = voidPaymentTx(bookingId, paymentId, userId, allowCompleted);
        repo.recomputePaymentStatus(bookingId);
        return voided;
    }

    /** {@code void_payment_tx} — returns the PRE-void row. */
    private PaymentEntryRow voidPaymentTx(long bookingId, long paymentId, long userId,
            boolean allowCompleted) {
        List<PaymentEntryRow> existingRows = jdbc.query(
                "SELECT " + PaymentRepo.PAYMENT_ENTRY_COLS
                        + " FROM payments WHERE id = ? AND booking_id = ? FOR UPDATE",
                PaymentRepo.PAYMENT_ENTRY_ROW, paymentId, bookingId);
        if (existingRows.isEmpty()) {
            throw ApiError.notFound("Payment not found");
        }
        PaymentEntryRow existing = existingRows.get(0);
        if ("refund".equals(existing.paymentType())) {
            throw ApiError.badRequest(
                    "Refund records are managed by the refund revert workflow");
        }
        if ("refunded".equals(existing.paymentStatus())
                || "void".equals(existing.paymentStatus())) {
            throw ApiError.badRequest("Payment is already in a terminal state");
        }
        if ("completed".equals(existing.paymentStatus()) && !allowCompleted) {
            throw ApiError.forbidden(
                    "Voiding a posted payment requires the payments:manage permission");
        }

        jdbc.update("""
                UPDATE payments SET status = 'void', processed_at = CURRENT_TIMESTAMP,
                    processed_by = ? WHERE id = ?
                """, userId, paymentId);

        if ("deposit".equals(existing.paymentType())) {
            repo.syncBookingDepositMirror(bookingId);
        }
        return existing;
    }

    /**
     * {@code reject_payment_by}'s transaction body — void the claim and return
     * the booking to the payment-awaiting state.
     */
    @Transactional
    public boolean rejectPaymentTx(Long actorUserId, long paymentId, long bookingId,
            String reason) {
        repo.lockBookingForPayment(bookingId);
        String status = repo.lockPaymentStatus(paymentId, bookingId);
        if (status == null) {
            throw ApiError.notFound("Payment not found.");
        }
        if (!"pending".equals(status)) {
            throw ApiError.badRequest("Payment is no longer pending.");
        }
        Long rejected = repo.markPaymentRejected(paymentId, actorUserId, reason);
        if (rejected == null) {
            throw ApiError.badRequest("Payment is no longer pending.");
        }

        boolean resetToPayment = jdbc.update("""
                UPDATE bookings SET status = 'pending_payment', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'pending_confirmation'
                """, bookingId) == 1;
        if (resetToPayment) {
            bookings.recordBookingHistory(bookingId, "pending_confirmation", "pending_payment",
                    actorUserId, "Payment claim rejected; awaiting another payment",
                    Map.of("payment_id", paymentId));
        }
        audit.event(actorUserId, "payment_rejected", "payment", paymentId,
                Map.of("booking_id", bookingId, "reason", reason));
        return resetToPayment;
    }

    /** {@code refund_deposit}'s transaction body. */
    @Transactional
    public PaymentEntryRow refundDepositTx(long userId, long bookingId,
            String paymentMethod, BigDecimal depositAmount) {
        repo.lockBookingForPayment(bookingId);

        List<Long> existingRefund = jdbc.query("""
                SELECT id FROM payments WHERE booking_id = ? AND payment_type = 'refund'
                  AND notes = 'Keycard deposit refund' AND status = 'refunded' LIMIT 1
                """, (rs, i) -> rs.getLong(1), bookingId);
        if (!existingRefund.isEmpty()) {
            throw ApiError.badRequest("Deposit already refunded");
        }

        BigDecimal refundableDeposit = jdbc.queryForObject("""
                SELECT
                    COALESCE((SELECT SUM(amount) FROM payments
                              WHERE booking_id = ? AND payment_type = 'deposit' AND status = 'completed'), 0)
                    - COALESCE((SELECT SUM(amount) FROM payments
                              WHERE booking_id = ? AND payment_type = 'refund' AND status = 'refunded'), 0)
                """, BigDecimal.class, bookingId, bookingId);
        if (refundableDeposit == null || refundableDeposit.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiError.badRequest("No refundable deposit was collected for this booking");
        }
        if (depositAmount.compareTo(refundableDeposit) > 0) {
            throw ApiError.badRequest(
                    "Deposit refund amount cannot exceed the refundable deposit of "
                            + refundableDeposit);
        }

        return jdbc.query("""
                INSERT INTO payments (
                    uuid, booking_id, amount, payment_method, payment_type,
                    status, notes, created_by
                )
                VALUES (gen_uuidv7(), ?, ?, ?, 'refund', 'refunded',
                        'Keycard deposit refund', ?)
                RETURNING id, booking_id, amount::text AS total_amount, payment_method,
                          payment_type, status AS payment_status,
                          NULL::text AS transaction_reference, notes,
                          created_at::date::text AS payment_date, created_at,
                          idempotency_fingerprint
                """, PaymentRepo.PAYMENT_ENTRY_ROW, bookingId, depositAmount,
                paymentMethod, userId).get(0);
    }

    /** {@code revert_deposit_refund}'s transaction body — void the refund marker. */
    @Transactional
    public long revertDepositRefundTx(long bookingId) {
        List<Long> refundIds = jdbc.query("""
                SELECT id FROM payments WHERE booking_id = ? AND payment_type = 'refund'
                  AND notes = 'Keycard deposit refund' AND status = 'refunded'
                ORDER BY id DESC LIMIT 1
                """, (rs, i) -> rs.getLong(1), bookingId);
        if (refundIds.isEmpty()) {
            throw ApiError.badRequest("No deposit refund to revert");
        }
        long refundId = refundIds.get(0);
        jdbc.update("UPDATE payments SET status = 'void' WHERE id = ?", refundId);
        return refundId;
    }
}
