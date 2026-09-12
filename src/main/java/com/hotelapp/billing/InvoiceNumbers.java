package com.hotelapp.billing;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of {@code services/invoice_numbers.rs::next_invoice_number} and
 * {@code services/payments.rs::ensure_invoice_for_booking}: the checkout-time
 * invoice minted for a booking that has none. Reuses an existing invoice row
 * or the number a customer-ledger entry already carries, and only mints a new
 * {@code INV-YYYYMM-XXXX} sequence value as a last resort.
 */
@Component
public class InvoiceNumbers {

    private final JdbcTemplate jdbc;

    public InvoiceNumbers(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code next_invoice_number}: {@code INV-<YYYYMM>-<seq+1>} for the hotel month. */
    private String nextInvoiceNumber() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT TO_CHAR(CURRENT_DATE, 'YYYYMM') AS yyyymm, MAX(seq) AS max_seq
                FROM (
                    SELECT CAST(SUBSTRING(invoice_number FROM 12) AS BIGINT) AS seq
                    FROM invoices
                    WHERE invoice_number LIKE 'INV-' || TO_CHAR(CURRENT_DATE, 'YYYYMM') || '-%'
                    UNION ALL
                    SELECT CAST(SUBSTRING(invoice_number FROM 12) AS BIGINT) AS seq
                    FROM customer_ledgers
                    WHERE invoice_number LIKE 'INV-' || TO_CHAR(CURRENT_DATE, 'YYYYMM') || '-%'
                ) combined
                """);
        String yyyymm = String.valueOf(row.get("yyyymm"));
        long next = (row.get("max_seq") == null ? 0 : ((Number) row.get("max_seq")).longValue()) + 1;
        return String.format("INV-%s-%04d", yyyymm, next);
    }

    /**
     * {@code ensure_invoice_for_booking}: idempotent — returns the existing
     * invoice/ledger number when one already exists, else inserts and returns
     * the freshly minted number.
     */
    @Transactional
    public String ensureInvoiceForBooking(long bookingId, long userId) {
        List<String> existing = jdbc.query(
                "SELECT invoice_number FROM invoices WHERE booking_id = ? LIMIT 1",
                (rs, i) -> rs.getString(1), bookingId);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        List<String> ledger = jdbc.query(
                "SELECT invoice_number FROM customer_ledgers "
                        + "WHERE booking_id = ? AND invoice_number IS NOT NULL "
                        + "ORDER BY id LIMIT 1",
                (rs, i) -> rs.getString(1), bookingId);
        String invoiceNumber = ledger.isEmpty() ? nextInvoiceNumber() : ledger.get(0);
        jdbc.update("""
                INSERT INTO invoices (
                    invoice_number, booking_id, billing_name, billing_email,
                    subtotal, total_amount, line_items, status, invoice_type, created_by
                )
                SELECT ?, b.id,
                       COALESCE(g.nick_name, ''),
                       g.email,
                       b.total_amount,
                       b.total_amount,
                       '[]'::jsonb,
                       'issued',
                       'booking',
                       ?
                FROM bookings b
                INNER JOIN guests g ON b.guest_id = g.id
                WHERE b.id = ?
                """, invoiceNumber, userId, bookingId);
        return invoiceNumber;
    }
}
