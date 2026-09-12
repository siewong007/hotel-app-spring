package com.hotelapp.ops;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.web.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of routes/settings.rs, audit.rs, night_audit.rs and data_transfer.rs.
 */
@RestController
public class OpsController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public OpsController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/settings")
    public Map<String, Object> settings(@RequestParam Map<String, String> q) {
        long page = Page.page(q);
        long size = Page.pageSize(q);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM system_settings", Long.class);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT key, value, value_type, category, description, is_public "
                        + "FROM system_settings ORDER BY category, key LIMIT ? OFFSET ?",
                size, Page.offset(page, size));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @GetMapping("/api/settings/public")
    public List<Map<String, Object>> publicSettings() {
        return jdbc.queryForList(
                "SELECT key, value FROM system_settings WHERE is_public ORDER BY key");
    }

    @PatchMapping("/api/settings/{key}")
    public Map<String, Object> updateSetting(@PathVariable String key,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate(userId, "settings:manage");
        int updated = jdbc.update(
                "UPDATE system_settings SET value = ?, updated_at = NOW(), updated_by = ? "
                        + "WHERE key = ?", str(body, "value"), userId, key);
        if (updated == 0) {
            throw ApiError.notFound("Setting not found");
        }
        audit.event(userId, "setting_updated", "system_setting", null, Map.of("key", key));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT key, value, value_type, category, description, is_public "
                        + "FROM system_settings WHERE key = ?", key);
        return rows.get(0);
    }

    @GetMapping("/api/audit-logs")
    public Map<String, Object> auditLogs(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        gateAnyOf(userId, List.of("audit:read", "audit:manage"));
        long page = Page.page(q);
        long size = Math.min(Page.pageSize(q), 200);
        var clauses = new java.util.ArrayList<String>();
        var args = new java.util.ArrayList<Object>();
        if (notBlank(q.get("action"))) {
            clauses.add("action = ?");
            args.add(q.get("action"));
        }
        if (notBlank(q.get("user_id"))) {
            clauses.add("user_id = ?");
            args.add(Long.parseLong(q.get("user_id")));
        }
        if (notBlank(q.get("resource_type"))) {
            clauses.add("resource_type = ?");
            args.add(q.get("resource_type"));
        }
        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs al " + where, Long.class, args.toArray());
        List<Map<String, Object>> data = jdbc.queryForList("""
                SELECT al.*, u.username AS username FROM audit_logs al
                LEFT JOIN users u ON u.id = al.user_id
                """ + where + " ORDER BY al.created_at DESC LIMIT ? OFFSET ?",
                append(args, size, Page.offset(page, size)));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @GetMapping("/api/audit-logs/actions")
    public List<Map<String, Object>> auditActions() {
        gateAnyOf(CurrentUser.require().userId(), List.of("audit:read", "audit:manage"));
        return jdbc.queryForList(
                "SELECT DISTINCT action FROM audit_logs ORDER BY action LIMIT 500");
    }

    @GetMapping("/api/audit-logs/users")
    public List<Map<String, Object>> auditUsers() {
        gateAnyOf(CurrentUser.require().userId(), List.of("audit:read", "audit:manage"));
        return jdbc.queryForList("""
                SELECT DISTINCT al.user_id, u.username FROM audit_logs al
                LEFT JOIN users u ON u.id = al.user_id
                WHERE al.user_id IS NOT NULL ORDER BY u.username
                """);
    }

    @GetMapping("/api/audit-logs/resource-types")
    public List<Map<String, Object>> auditResourceTypes() {
        gateAnyOf(CurrentUser.require().userId(), List.of("audit:read", "audit:manage"));
        return jdbc.queryForList(
                "SELECT DISTINCT resource_type FROM audit_logs ORDER BY resource_type");
    }

    @GetMapping(value = "/api/audit-logs/export/csv", produces = "text/csv")
    public String exportCsv(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        gateAnyOf(userId, List.of("audit:export", "audit:read", "audit:manage"));
        StringBuilder csv = new StringBuilder("id,created_at,user_id,action,resource_type,"
                + "resource_id\n");
        jdbc.queryForList("""
                SELECT id, created_at, user_id, action, resource_type, resource_id
                FROM audit_logs ORDER BY created_at DESC LIMIT 10000
                """).forEach(row -> {
            for (String column : new String[] {"id", "created_at", "user_id", "action",
                    "resource_type", "resource_id"}) {
                csv.append(csvCell(row.get(column))).append(',');
            }
            csv.append('\n');
        });
        audit.event(userId, "audit_exported", "audit_log", null, null);
        return csv.toString();
    }

    static String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        if (text.startsWith("=") || text.startsWith("+") || text.startsWith("-")
                || text.startsWith("@")) {
            text = "'" + text;
        }
        return "\"" + text + "\"";
    }

    @GetMapping("/api/night-audit/preview")
    public Map<String, Object> nightAuditPreview() {
        gate(CurrentUser.require().userId(), "night_audit:run");
        return previewBody();
    }

    private Map<String, Object> previewBody() {
        return jdbc.queryForMap("""
                SELECT CURRENT_DATE AS business_date,
                       COUNT(*) FILTER (WHERE status = 'checked_in') AS active_stays,
                       COUNT(*) FILTER (WHERE status IN ('reserved','confirmed')
                           AND check_in_date <= CURRENT_DATE) AS pending_arrivals,
                       COUNT(*) FILTER (WHERE check_out_date < CURRENT_DATE
                           AND status = 'checked_in') AS overdue_checkouts,
                       COALESCE(SUM(total_amount) FILTER (
                           WHERE status = 'checked_out'
                             AND posted_date IS NULL), 0) AS revenue_to_post
                FROM bookings
                """);
    }

    @PostMapping("/api/night-audit/run")
    public Map<String, Object> runNightAudit(@RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate(userId, "night_audit:run");
        int checkedOut = jdbc.update("""
                UPDATE bookings SET status = 'checked_out', actual_check_out =
                    COALESCE(actual_check_out, NOW()), posted_date = CURRENT_DATE
                WHERE check_out_date < CURRENT_DATE AND status = 'checked_in'
                """);
        Long runId = jdbc.queryForObject("""
                INSERT INTO night_audit_runs (audit_date, run_by, status,
                    total_bookings_posted, notes)
                VALUES (CURRENT_DATE, ?, 'completed', ?, ?)
                RETURNING id
                """, Long.class, userId, checkedOut, str(body, "notes"));
        audit.event(userId, "night_audit_run", "night_audit_run", runId,
                Map.of("bookings_posted", checkedOut));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM night_audit_runs WHERE id = ?", runId);
        return rows.isEmpty() ? previewBody() : rows.get(0);
    }

    private void gate(long userId, String permission) {
        com.hotelapp.core.security.PermissionGateHelper.check(userId, permission);
    }

    private void gateAnyOf(long userId, List<String> permissions) {
        com.hotelapp.core.security.PermissionGateHelper.checkAny(userId, permissions);
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static Object[] append(List<Object> base, Object... extra) {
        base.addAll(java.util.Arrays.asList(extra));
        return base.toArray();
    }
}
