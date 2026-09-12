package com.hotelapp.ekyc;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.web.Page;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff + account eKYC surface (modules/ekyc). Portal self-service eKYC
 * ({@code /guest-portal/me/ekyc*}) is Task 4d.
 */
@RestController
public class EkycController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public EkycController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/ekyc/status")
    public Map<String, Object> ekycStatus() {
        long userId = CurrentUser.require().userId();
        Long guestId = jdbc.queryForObject(
                "SELECT guest_id FROM users WHERE id = ?", Long.class, userId);
        if (guestId == null) {
            throw ApiError.badRequest("This account has no linked guest profile");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ekyc_verifications WHERE guest_id = ? ORDER BY created_at DESC "
                        + "LIMIT 1", guestId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("has_submission", !rows.isEmpty());
        body.put("verification", rows.isEmpty() ? null : rows.get(0));
        return body;
    }

    @PostMapping("/api/ekyc/upload-document")
    public Map<String, Object> uploadDocument(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        String kind = str(body, "kind");
        if (kind == null) {
            throw ApiError.badRequest("Document kind is required");
        }
        jdbc.update("""
                INSERT INTO guest_documents (guest_id, document_type, file_path, uploaded_by)
                SELECT COALESCE(guest_id, 0), ?, ?, ? FROM users WHERE id = ?
                """, kind, str(body, "content_ref"), userId, userId);
        audit.event(userId, "ekyc_document_uploaded", "guest_document", null,
                Map.of("kind", kind));
        return message("Document uploaded successfully");
    }

    @PostMapping("/api/ekyc/submit")
    public Map<String, Object> submit(@RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        Long guestId = jdbc.queryForObject(
                "SELECT guest_id FROM users WHERE id = ?", Long.class, userId);
        if (guestId == null) {
            throw ApiError.badRequest("This account has no linked guest profile");
        }
        jdbc.update("""
                INSERT INTO ekyc_verifications (guest_id, status)
                VALUES (?, 'pending')
                """, guestId);
        audit.event(userId, "ekyc_submitted", "ekyc_verification", guestId, null);
        return message("eKYC submitted successfully");
    }

    @GetMapping("/api/ekyc/admin/applications")
    public Map<String, Object> applications(@RequestParam Map<String, String> q) {
        gateAny(CurrentUser.require().userId(), List.of("ekyc:review", "ekyc:manage"));
        long page = Page.page(q);
        long size = Page.pageSize(q);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM ekyc_verifications", Long.class);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT * FROM ekyc_verifications ORDER BY created_at DESC LIMIT ? OFFSET ?",
                size, Page.offset(page, size));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/ekyc/admin/applications/{id}/actions")
    public Map<String, Object> applicationAction(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gateAny(userId, List.of("ekyc:review", "ekyc:manage"));
        String action = str(body, "action");
        if (!List.of("approve", "reject", "request_resubmission").contains(action)) {
            throw ApiError.badRequest("Unsupported action");
        }
        int updated = jdbc.update("""
                UPDATE ekyc_verifications SET status = CASE
                    WHEN ? = 'approve' THEN 'approved'
                    WHEN ? = 'reject' THEN 'rejected'
                    ELSE 'resubmission_required' END,
                    reviewed_at = NOW(), reviewed_by = ?
                WHERE id = ?
                """, action, action, userId, id);
        if (updated == 0) {
            throw ApiError.notFound("Application not found");
        }
        audit.event(userId, "ekyc_" + action + "_actioned", "ekyc_verification", id,
                Map.of("reason", body.getOrDefault("reason", "")));
        return jdbc.queryForMap("SELECT * FROM ekyc_verifications WHERE id = ?", id);
    }

    @GetMapping("/api/ekyc/admin/dashboard")
    public Map<String, Object> dashboard() {
        gateAny(CurrentUser.require().userId(), List.of("ekyc:review", "ekyc:manage"));
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending,
                       SUM(CASE WHEN status = 'approved' THEN 1 ELSE 0 END) AS approved,
                       SUM(CASE WHEN status = 'rejected' THEN 1 ELSE 0 END) AS rejected
                FROM ekyc_verifications
                """);
    }

    @GetMapping("/api/ekyc/admin/reason-codes")
    public List<Map<String, Object>> reasonCodes() {
        gateAny(CurrentUser.require().userId(), List.of("ekyc:review", "ekyc:manage"));
        return jdbc.queryForList("SELECT * FROM ekyc_reason_codes ORDER BY code");
    }

    private void gateAny(long userId, List<String> permissions) {
        com.hotelapp.core.security.PermissionGateHelper.checkAny(userId, permissions);
    }
}
