package com.hotelapp.payments;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.payments.PaymentModels.PaymentEntryRow;
import com.hotelapp.payments.PaymentModels.PaymentReceiptFile;
import com.hotelapp.payments.PaymentModels.PendingPaymentEntry;
import com.hotelapp.payments.PaymentModels.WorkflowSummaryRow;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * Port of {@code repositories::payment} — the staff payments SQL. Methods run
 * on the caller's connection, so the {@code *_tx} upstream variants map to
 * calls made inside a {@link org.springframework.transaction.annotation.Transactional}
 * boundary.
 */
@Component
public class PaymentRepo {

    static final RowMapper<PaymentEntryRow> PAYMENT_ENTRY_ROW = (rs, i) -> new PaymentEntryRow(
            rs.getLong("id"),
            rs.getLong("booking_id"),
            rs.getString("total_amount"),
            rs.getString("payment_method"),
            rs.getString("payment_type"),
            rs.getString("payment_status"),
            rs.getString("transaction_reference"),
            rs.getString("notes"),
            rs.getString("payment_date"),
            rs.getObject("created_at"),
            rs.getString("idempotency_fingerprint"));

    static final String PAYMENT_ENTRY_COLS =
            "id, booking_id, amount::text AS total_amount, payment_method, payment_type, "
                    + "status AS payment_status, transaction_id AS transaction_reference, notes, "
                    + "created_at::date::text AS payment_date, created_at, idempotency_fingerprint";

    static final String PENDING_ENTRY_COLS = """
            p.id, p.booking_id, b.booking_number, b.guest_id AS guest_id,
            g.nick_name AS guest_name, p.amount::text AS amount,
            p.payment_method, p.status,
            p.gateway_payment_intent_id AS reference, p.notes AS notes,
            p.created_at::text AS created_at,
            EXISTS(SELECT 1 FROM payment_receipt_requests pr WHERE pr.payment_id = p.id) AS receipt_requested,
            EXISTS(SELECT 1 FROM payment_receipt_requests pr WHERE pr.payment_id = p.id AND pr.uploaded_at IS NOT NULL) AS receipt_uploaded,
            EXISTS(SELECT 1 FROM payment_receipt_requests pr WHERE pr.payment_id = p.id AND pr.receipt_path IS NOT NULL) AS receipt_file_available,
            p.processed_at::text AS processed_at, reviewer.full_name AS processed_by_name,
            p.failure_reason AS decision_reason
            """;

    static final String PENDING_ENTRY_FROM = """
            FROM payments p
            JOIN bookings b ON b.id = p.booking_id
            LEFT JOIN guests g ON g.id = b.guest_id
            LEFT JOIN users reviewer ON reviewer.id = p.processed_by
            """;

    static final RowMapper<PendingPaymentEntry> PENDING_ENTRY_ROW = (rs, i) -> new PendingPaymentEntry(
            rs.getLong("id"),
            rs.getLong("booking_id"),
            rs.getString("booking_number"),
            rs.getObject("guest_id") == null ? null : rs.getLong("guest_id"),
            rs.getString("guest_name"),
            rs.getString("amount"),
            rs.getString("payment_method"),
            rs.getString("status"),
            rs.getString("reference"),
            rs.getString("notes"),
            rs.getString("created_at"),
            rs.getBoolean("receipt_requested"),
            rs.getBoolean("receipt_uploaded"),
            rs.getBoolean("receipt_file_available"),
            rs.getString("processed_at"),
            rs.getString("processed_by_name"),
            rs.getString("decision_reason"));

    private final JdbcTemplate jdbc;

    public PaymentRepo(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code canonical_payment_fingerprint} — sha256 over the field tuple. */
    public static String canonicalPaymentFingerprint(long bookingId, BigDecimal amount,
            String paymentMethod, String paymentType, String transactionReference,
            String notes, String paymentDate) {
        StringBuilder payload = new StringBuilder();
        appendField(payload, "booking_id", Long.toString(bookingId));
        appendField(payload, "amount", amount == null ? null : amount.stripTrailingZeros().toPlainString());
        appendField(payload, "payment_method", paymentMethod);
        appendField(payload, "payment_type", paymentType);
        appendField(payload, "transaction_reference", transactionReference);
        appendField(payload, "notes", notes);
        appendField(payload, "payment_date", paymentDate);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw ApiError.internal("SHA-256 unavailable");
        }
    }

    private static void appendField(StringBuilder payload, String name, String value) {
        payload.append(name).append(':');
        if (value == null) {
            payload.append('N');
        } else {
            payload.append('S').append(':').append(value.length()).append(':').append(value);
        }
        payload.append('|');
    }

    /** {@code lock_booking_for_payment_tx}. */
    public void lockBookingForPayment(long bookingId) {
        List<Long> rows = jdbc.query(
                "SELECT id FROM bookings WHERE id = ? FOR UPDATE",
                (rs, i) -> rs.getLong(1), bookingId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
    }

    /** {@code find_idempotent_payment_tx}. */
    public PaymentEntryRow findIdempotentPayment(long bookingId, String idempotencyKey) {
        List<PaymentEntryRow> rows = jdbc.query(
                "SELECT " + PAYMENT_ENTRY_COLS + " FROM payments "
                        + "WHERE booking_id = ? AND idempotency_key = ? LIMIT 1",
                PAYMENT_ENTRY_ROW, bookingId, idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code lock_transaction_references_tx} — advisory locks, sorted+deduped. */
    public void lockTransactionReferences(List<String> transactionReferences) {
        List<String> references = transactionReferences.stream()
                .filter(value -> value != null && !value.isEmpty())
                .distinct().sorted().toList();
        for (String reference : references) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                    rs -> null, reference);
        }
    }

    /**
     * {@code list_keyed_reference_payments_tx} — KEYED rows only; legacy
     * pre-fingerprint rows carry no key to replay against and can only ever
     * conflict, so they are excluded.
     */
    public List<PaymentEntryRow> listKeyedReferencePayments(String transactionReference) {
        return jdbc.query(
                "SELECT " + PAYMENT_ENTRY_COLS + " FROM payments "
                        + "WHERE transaction_id = ? AND idempotency_fingerprint IS NOT NULL "
                        + "ORDER BY id",
                PAYMENT_ENTRY_ROW, transactionReference);
    }

    /** {@code transaction_reference_owner_booking_tx}. */
    public Long transactionReferenceOwnerBooking(String transactionReference, long paymentId) {
        List<Long> rows = jdbc.query(
                "SELECT booking_id FROM payments WHERE transaction_id = ? AND id <> ? "
                        + "AND idempotency_fingerprint IS NOT NULL ORDER BY id LIMIT 1",
                (rs, i) -> rs.getLong(1), transactionReference, paymentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code booking_label_tx} — `BK-1234 (room 101)`, else `#id`. */
    public String bookingLabel(long bookingId) {
        List<String[]> rows = jdbc.query(
                "SELECT b.booking_number, r.room_number FROM bookings b "
                        + "LEFT JOIN rooms r ON r.id = b.room_id WHERE b.id = ?",
                (rs, i) -> new String[] {rs.getString(1), rs.getString(2)}, bookingId);
        if (rows.isEmpty()) {
            return "#" + bookingId;
        }
        String bookingNumber = rows.get(0)[0];
        String roomNumber = rows.get(0)[1];
        if (bookingNumber != null && roomNumber != null) {
            return bookingNumber + " (room " + roomNumber + ")";
        }
        if (bookingNumber != null) {
            return bookingNumber;
        }
        return "#" + bookingId;
    }

    /** {@code transaction_reference_conflict_tx} — the actionable 409 body. */
    public ApiError transactionReferenceConflict(long ownerBookingId, long conflictingBookingId) {
        if (ownerBookingId == conflictingBookingId) {
            return ApiError.conflict(
                    "This reference is already on another payment for this booking, recorded "
                            + "with different details. Use a different reference, or leave it "
                            + "blank.");
        }
        String label = bookingLabel(ownerBookingId);
        return ApiError.conflict(
                "This reference is already recorded on booking " + label + ". A reference "
                        + "identifies one incoming payment, so it cannot be credited to two "
                        + "bookings. Use a different reference, or leave it blank.");
    }

    /** {@code payment_booking_id}. */
    public long paymentBookingId(long paymentId) {
        List<Long> rows = jdbc.query(
                "SELECT booking_id FROM payments WHERE id = ?",
                (rs, i) -> rs.getLong(1), paymentId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Payment not found");
        }
        return rows.get(0);
    }

    /** {@code lock_payment_status_tx}. */
    public String lockPaymentStatus(long paymentId, long bookingId) {
        List<String> rows = jdbc.query(
                "SELECT status FROM payments WHERE id = ? AND booking_id = ? FOR UPDATE",
                (rs, i) -> rs.getString(1), paymentId, bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code get_payment_for_review}. */
    public PendingPaymentEntry getPaymentForReview(long paymentId) {
        List<PendingPaymentEntry> rows = jdbc.query(
                "SELECT " + PENDING_ENTRY_COLS + PENDING_ENTRY_FROM + "WHERE p.id = ?",
                PENDING_ENTRY_ROW, paymentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code list_pending_payments}. */
    public PendingEntryPage listPendingPayments(long limit, long offset) {
        List<PendingPaymentEntry> items = jdbc.query(
                "SELECT " + PENDING_ENTRY_COLS + PENDING_ENTRY_FROM
                        + "WHERE p.status = 'pending' ORDER BY p.created_at DESC LIMIT ? OFFSET ?",
                PENDING_ENTRY_ROW, limit, offset);
        long total = countPayments("SELECT COUNT(*) FROM payments WHERE status = 'pending'");
        return new PendingEntryPage(items, total);
    }

    /** {@code list_payment_approval_history}. */
    public PendingEntryPage listPaymentApprovalHistory(long limit, long offset) {
        List<PendingPaymentEntry> items = jdbc.query(
                "SELECT " + PENDING_ENTRY_COLS + PENDING_ENTRY_FROM
                        + "WHERE p.payment_method IN ('bank_transfer', 'paypal') "
                        + "AND p.status IN ('completed', 'void') "
                        + "ORDER BY p.processed_at DESC NULLS LAST, p.created_at DESC "
                        + "LIMIT ? OFFSET ?",
                PENDING_ENTRY_ROW, limit, offset);
        long total = countPayments(
                "SELECT COUNT(*) FROM payments WHERE payment_method IN ('bank_transfer', 'paypal') "
                        + "AND status IN ('completed', 'void')");
        return new PendingEntryPage(items, total);
    }

    record PendingEntryPage(List<PendingPaymentEntry> items, long total) {
    }

    private long countPayments(String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    /** {@code list_payment_entries} — `get_all_payments`' row source. */
    public List<PaymentEntryRow> listPaymentEntries(long bookingId) {
        return jdbc.query(
                "SELECT id, booking_id, amount::text AS total_amount, payment_method, payment_type, "
                        + "status AS payment_status, transaction_id AS transaction_reference, notes, "
                        + "created_at::date::text AS payment_date, created_at, "
                        + "idempotency_fingerprint "
                        + "FROM payments WHERE booking_id = ? ORDER BY created_at ASC",
                PAYMENT_ENTRY_ROW, bookingId);
    }

    /** {@code request_receipt} — upsert the per-payment request row. */
    public void requestReceipt(long paymentId, long requestedBy, String message) {
        jdbc.update("""
                INSERT INTO payment_receipt_requests (payment_id, requested_by, request_message)
                VALUES (?, ?, ?)
                ON CONFLICT (payment_id) DO UPDATE SET
                    requested_by = EXCLUDED.requested_by,
                    request_message = EXCLUDED.request_message,
                    requested_at = CURRENT_TIMESTAMP
                """, paymentId, requestedBy, message);
    }

    /** {@code receipt_file}. */
    public PaymentReceiptFile receiptFile(long paymentId) {
        List<PaymentReceiptFile> rows = jdbc.query(
                "SELECT receipt_path, receipt_content_type FROM payment_receipt_requests "
                        + "WHERE payment_id = ? AND receipt_path IS NOT NULL",
                (rs, i) -> new PaymentReceiptFile(rs.getString(1), rs.getString(2)), paymentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code expired_receipt_request_payment_ids}. */
    public List<Long> expiredReceiptRequestPaymentIds() {
        return jdbc.query("""
                SELECT p.id FROM payments p
                JOIN payment_receipt_requests pr ON pr.payment_id = p.id
                WHERE p.status = 'pending' AND p.payment_method = 'bank_transfer'
                  AND pr.uploaded_at IS NULL
                  AND pr.requested_at <= CURRENT_TIMESTAMP - INTERVAL '1 day'
                """, (rs, i) -> rs.getLong(1));
    }

    /**
     * {@code expired_paypal_attempt_ids} — pending PayPal orders never handed
     * to capture. `processing` is deliberately excluded: its capture result
     * may be unknown and must never be reopened automatically.
     */
    public List<Long> expiredPaypalAttemptIds() {
        return jdbc.query("""
                SELECT id FROM payments WHERE status = 'pending' AND payment_method = 'paypal'
                  AND gateway_payment_intent_id IS NOT NULL
                  AND TRIM(gateway_payment_intent_id) <> ''
                  AND created_at <= CURRENT_TIMESTAMP - INTERVAL '10 minutes'
                """, (rs, i) -> rs.getLong(1));
    }

    /** {@code mark_payment_rejected_tx} — CAS pending -> void. */
    public Long markPaymentRejected(long paymentId, Long rejectedBy, String reason) {
        List<Long> rows = jdbc.query("""
                UPDATE payments
                SET status = 'void', failure_reason = ?,
                    processed_at = CURRENT_TIMESTAMP, processed_by = ?
                WHERE id = ? AND status = 'pending'
                RETURNING id
                """, (rs, i) -> rs.getLong(1), reason, rejectedBy, paymentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code workflow_summary_row}. */
    public WorkflowSummaryRow workflowSummaryRow(long bookingId) {
        List<WorkflowSummaryRow> rows = jdbc.query("""
                SELECT
                    b.id AS booking_id,
                    b.status AS booking_status,
                    COALESCE(b.payment_status, 'unpaid') AS payment_status,
                    b.total_amount,
                    COALESCE(b.tourism_tax_amount, 0) AS tourism_tax_amount,
                    COALESCE(b.extra_bed_charge, 0) AS extra_bed_charge,
                    COALESCE((SELECT SUM(p.amount) FROM payments p
                        WHERE p.booking_id = b.id AND p.status = 'completed'
                          AND COALESCE(p.payment_type, 'booking') != 'refund'), 0) AS total_paid,
                    COALESCE((SELECT SUM(p.amount) FROM payments p
                        WHERE p.booking_id = b.id AND p.status <> 'void'
                          AND (p.status = 'refunded' OR COALESCE(p.payment_type, 'booking') = 'refund')), 0) AS total_refunded,
                    COALESCE((SELECT SUM(p.amount) FROM payments p
                        WHERE p.booking_id = b.id AND p.status = 'completed'
                          AND COALESCE(p.payment_type, 'booking') = 'deposit'), 0) AS deposit_collected,
                    COALESCE((SELECT SUM(p.amount) FROM payments p
                        WHERE p.booking_id = b.id AND p.status <> 'void'
                          AND (p.status = 'refunded' OR COALESCE(p.payment_type, 'booking') = 'refund')), 0) AS deposit_refunded,
                    EXISTS(SELECT 1 FROM payments p WHERE p.booking_id = b.id AND p.status = 'failed') AS has_failed_payment
                FROM bookings b
                WHERE b.id = ?
                """, (rs, i) -> new WorkflowSummaryRow(
                rs.getString("booking_status"),
                rs.getString("payment_status"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("tourism_tax_amount"),
                rs.getBigDecimal("extra_bed_charge"),
                rs.getBigDecimal("total_paid"),
                rs.getBigDecimal("total_refunded"),
                rs.getBigDecimal("deposit_collected"),
                rs.getBigDecimal("deposit_refunded"),
                rs.getBoolean("has_failed_payment")), bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** {@code recompute_booking_payment_status} (pool and tx are identical SQL). */
    public void recomputePaymentStatus(long bookingId) {
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

    /** {@code sync_booking_deposit_mirror_tx}. */
    public void syncBookingDepositMirror(long bookingId) {
        jdbc.update("""
                UPDATE bookings b SET
                    deposit_paid = COALESCE(s.total, 0) > 0,
                    deposit_amount = NULLIF(s.total, 0),
                    deposit_paid_at = CASE WHEN COALESCE(s.total, 0) > 0
                        THEN COALESCE(b.deposit_paid_at, CURRENT_TIMESTAMP) ELSE NULL END,
                    updated_at = CURRENT_TIMESTAMP
                FROM (SELECT SUM(amount) AS total FROM payments
                      WHERE booking_id = ? AND payment_type = 'deposit' AND status = 'completed') s
                WHERE b.id = ?
                """, bookingId, bookingId);
    }
}
