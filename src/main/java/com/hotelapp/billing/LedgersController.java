package com.hotelapp.billing;

import java.math.BigDecimal;

import static com.hotelapp.billing.BillingController.dec;
import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LedgersController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public LedgersController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/ledgers")
    public Map<String, Object> list(@RequestParam Map<String, String> q) {
        long page = Page(q);
        long size = PageSize(q);
        String type = q.get("transaction_type");
        String status = q.get("status");
        var clauses = new java.util.ArrayList<String>();
        var args = new java.util.ArrayList<Object>();
        if (type != null && !type.isBlank()) {
            clauses.add("transaction_type = ?");
            args.add(type);
        }
        if (status != null && !status.isBlank()) {
            clauses.add("status = ?");
            args.add(status);
        }
        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM customer_ledgers " + where,
                Long.class, args.toArray());
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT * FROM customer_ledgers " + where + " ORDER BY created_at DESC "
                        + "LIMIT ? OFFSET ?",
                append(args, size, (page - 1) * size));
        return Page(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/ledgers")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        Number bookingId = num(body, "booking_id");
        BigDecimal amount = dec(body.get("amount"));
        String folioType = String.valueOf(body.getOrDefault("folio_type", "guest"));
        Long id = jdbc.queryForObject("""
                INSERT INTO customer_ledgers (booking_id, guest_id, company_name,
                    transaction_type, description, amount, tax_amount, net_amount,
                    transaction_date, due_date, currency, status, folio_type, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, CAST(? AS date),
                        COALESCE(?,'USD'), 'unpaid', ?, ?)
                RETURNING id
                """, Long.class, bookingId, num(body, "guest_id"), str(body, "company_name"),
                str(body, "transaction_type"), str(body, "description"), amount,
                numD(body.get("tax_amount")), amount.add(numZ(body.get("tax_amount"))),
                str(body, "due_date"), str(body, "currency"), folioType, userId);
        audit.event(userId, "ledger_created", "customer_ledger", id, null);
        return one(id);
    }

    @GetMapping("/api/ledgers/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return one(id);
    }

    @PatchMapping("/api/ledgers/{id}")
    public Map<String, Object> update(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        one(id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("description", "amount", "due_date", "notes", "status")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE customer_ledgers SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(CurrentUser.require().userId(), "ledger_updated", "customer_ledger", id,
                null);
        return one(id);
    }

    @DeleteMapping("/api/ledgers/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        one(id);
        jdbc.update("DELETE FROM customer_ledgers WHERE id = ?", id);
        audit.event(userId, "ledger_deleted", "customer_ledger", id, null);
        return message("Ledger deleted successfully");
    }

    @PostMapping("/api/ledgers/{id}/void")
    public Map<String, Object> voidLedger(@PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        one(id);
        jdbc.update("UPDATE customer_ledgers SET status = 'void', void_at = NOW(), void_by = ?, "
                + "void_reason = ? WHERE id = ?", userId, str(body, "reason"), id);
        audit.event(userId, "ledger_voided", "customer_ledger", id, body);
        return one(id);
    }

    @PostMapping("/api/ledgers/{id}/reverse")
    public Map<String, Object> reverse(@PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        Map<String, Object> ledger = one(id);
        jdbc.update("UPDATE customer_ledgers SET is_reversal = true, status = 'reversed' "
                + "WHERE id = ?", id);
        jdbc.update("""
                INSERT INTO customer_ledgers (booking_id, guest_id, company_name,
                    transaction_type, description, amount, transaction_date, currency,
                    status, folio_type, original_transaction_id, is_reversal, created_by)
                SELECT booking_id, guest_id, company_name, transaction_type,
                       COALESCE(?, 'Reversal') || ' - ' || COALESCE(description, ''),
                       -amount, CURRENT_DATE, currency, 'paid', folio_type, id, true, ?
                FROM customer_ledgers WHERE id = ?
                """, str(body, "reason"), userId, id);
        audit.event(userId, "ledger_reversed", "customer_ledger", id, ledger);
        return one(id);
    }

    @PostMapping("/api/ledgers/{id}/payments")
    public Map<String, Object> addPayment(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        one(id);
        BigDecimal amount = dec(body.get("amount"));
        Long paymentId = jdbc.queryForObject("""
                INSERT INTO customer_ledger_payments (ledger_id, amount, payment_method,
                    payment_date, reference_number, notes)
                VALUES (?, ?, COALESCE(?, 'cash'), CURRENT_DATE, ?, ?) RETURNING id
                """, Long.class, id, amount, str(body, "payment_method"),
                str(body, "reference_number"), str(body, "notes"));
        jdbc.update("""
                UPDATE customer_ledgers
                SET paid_amount = COALESCE(paid_amount, 0) + ?,
                    balance_due = GREATEST(amount - COALESCE(paid_amount, 0) - ?, 0)
                WHERE id = ?
                """, amount, amount, id);
        audit.event(userId, "ledger_payment_created", "ledger_payment", paymentId, null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM customer_ledger_payments WHERE id = ?", paymentId);
        return rows.get(0);
    }

    @GetMapping("/api/ledgers/{id}/payments")
    public List<Map<String, Object>> payments(@PathVariable long id) {
        one(id);
        return jdbc.queryForList("SELECT * FROM customer_ledger_payments WHERE ledger_id = ? "
                + "ORDER BY payment_date", id);
    }

    @PatchMapping("/api/ledgers/{id}/payments/{paymentId}")
    public Map<String, Object> updatePayment(@PathVariable long id,
            @PathVariable long paymentId, @RequestBody Map<String, Object> body) {
        one(id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("amount", "payment_method", "reference_number",
                "notes", "payment_date")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE customer_ledger_payments SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ? AND ledger_id = ?", append(List.of(paymentId, id),
                    sets.values().toArray()));
        }
        audit.event(CurrentUser.require().userId(), "ledger_payment_updated", "ledger_payment",
                paymentId, null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM customer_ledger_payments WHERE id = ?", paymentId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Payment not found");
        }
        return rows.get(0);
    }

    @DeleteMapping("/api/ledgers/{id}/payments/{paymentId}")
    public Map<String, Object> deletePayment(@PathVariable long id,
            @PathVariable long paymentId) {
        long userId = CurrentUser.require().userId();
        one(id);
        Map<String, Object> payment = onePayment(id, paymentId);
        BigDecimal amount = dec(payment.get("amount"));
        jdbc.update("DELETE FROM customer_ledger_payments WHERE id = ? AND ledger_id = ?",
                paymentId, id);
        jdbc.update("""
                UPDATE customer_ledgers
                SET paid_amount = GREATEST(COALESCE(paid_amount, 0) - ?, 0)
                WHERE id = ?
                """, amount, id);
        audit.event(userId, "ledger_payment_deleted", "ledger_payment", paymentId, null);
        return message("Ledger payment deleted successfully");
    }

    @GetMapping("/api/ledgers/{id}/with-payments")
    public Map<String, Object> withPayments(@PathVariable long id) {
        Map<String, Object> ledger = one(id);
        ledger.put("payments", jdbc.queryForList(
                "SELECT * FROM customer_ledger_payments WHERE ledger_id = ?", id));
        return ledger;
    }

    @GetMapping("/api/ledgers/summary")
    public Map<String, Object> summary() {
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total_records,
                       COALESCE(SUM(amount), 0) AS total_amount,
                       COALESCE(SUM(paid_amount), 0) AS total_paid,
                       COALESCE(SUM(GREATEST(COALESCE(balance_due, amount), 0)), 0) AS total_outstanding,
                       SUM(CASE WHEN status = 'void' THEN 1 ELSE 0 END) AS void_count,
                       SUM(CASE WHEN is_reversal THEN 1 ELSE 0 END) AS reversal_count
                FROM customer_ledgers
                """);
    }

    @PostMapping("/api/ledgers/company-payments")
    public Map<String, Object> companyPayment(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        String companyName = str(body, "company_name");
        BigDecimal amount = dec(body.get("amount"));
        if (companyName == null || amount.signum() <= 0) {
            throw ApiError.badRequest("Company name and positive amount are required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO customer_ledger_payments (ledger_id, amount, payment_method,
                    payment_date, reference_number, notes)
                SELECT id, ?, COALESCE(?, 'bank_transfer'), CURRENT_DATE, ?, ?
                FROM customer_ledgers WHERE company_name = ? ORDER BY id LIMIT 1
                RETURNING id
                """, Long.class, amount, str(body, "payment_method"),
                str(body, "reference_number"), str(body, "notes"), companyName);
        if (id == null) {
            throw ApiError.notFound("No ledger found for this company");
        }
        jdbc.update("""
                UPDATE customer_ledgers SET paid_amount = COALESCE(paid_amount, 0) + ?
                WHERE company_name = ?
                """, amount, companyName);
        audit.event(userId, "company_payment_created", "ledger_payment", id,
                Map.of("company", companyName));
        return message("Company payment recorded successfully");
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM customer_ledgers WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Ledger not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> onePayment(long ledgerId, long paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM customer_ledger_payments WHERE id = ? AND ledger_id = ?",
                paymentId, ledgerId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Ledger payment not found");
        }
        return rows.get(0);
    }

    private static Map<String, Object> Page(Object data, long total, long page, long pageSize) {
        var body = new LinkedHashMap<String, Object>();
        body.put("data", data);
        body.put("total", total);
        body.put("page", page);
        body.put("page_size", pageSize);
        return body;
    }

    private static long Page(Map<String, String> q) {
        return parseLong(q.get("page"), 1);
    }

    private static long PageSize(Map<String, String> q) {
        return parseLong(q.get("page_size"), 20);
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Math.max(1, Long.parseLong(raw.trim()));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Object[] append(java.util.List<Object> base, Object... extra) {
        base.addAll(java.util.Arrays.asList(extra));
        return base.toArray();
    }

    private static BigDecimal numZ(Object value) {
        return value == null ? BigDecimal.ZERO : dec(value);
    }

    private static BigDecimal numD(Object value) {
        return value == null ? null : dec(value);
    }


}
