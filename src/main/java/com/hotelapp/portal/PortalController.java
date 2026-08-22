package com.hotelapp.portal;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.web.Page;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of modules/ekyc and guest-portal/guest_booking routes. Portal routes
 * authenticate with a portal session token issued by /verify; staff eKYC
 * admin routes use the standard permission gates.
 */
@RestController
public class PortalController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public PortalController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @PostMapping("/api/guest-portal/verify")
    public Map<String, Object> verify(@RequestBody Map<String, Object> body) {
        String bookingNumber = str(body, "booking_number");
        String lastName = str(body, "last_name");
        if (bookingNumber == null || lastName == null) {
            throw ApiError.badRequest("Booking number and last name are required");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id, b.guest_id FROM bookings b
                JOIN guests g ON g.id = b.guest_id
                WHERE b.booking_number = ?
                  AND LOWER(COALESCE(g.last_name,'')) = LOWER(?)
                """, bookingNumber, lastName);
        if (rows.isEmpty()) {
            throw ApiError.unauthorized("We could not match that booking");
        }
        long bookingId = ((Number) rows.get(0).get("id")).longValue();
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO guest_portal_sessions (booking_id, token, expires_at)
                VALUES (?, ?, NOW() + INTERVAL '14 days')
                ON CONFLICT DO NOTHING
                """, bookingId, token);
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("token", token);
        responseBody.put("booking_id", bookingId);
        return responseBody;
    }

    private Map<String, Object> portalBooking(String token) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.* FROM bookings b
                JOIN guest_portal_sessions s ON s.booking_id = b.id
                WHERE s.token = ? AND s.expires_at > NOW()
                """, token);
        if (rows.isEmpty()) {
            throw ApiError.unauthorized("Invalid or expired portal session");
        }
        return rows.get(0);
    }

    @GetMapping("/api/guest-portal/booking/{token}")
    public Map<String, Object> bookingByToken(@PathVariable String token) {
        return portalBooking(token);
    }

    @PostMapping("/api/guest-portal/pre-checkin/{token}")
    public Map<String, Object> preCheckin(@PathVariable String token,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> booking = portalBooking(token);
        jdbc.update("UPDATE bookings SET pre_checkin_completed = true WHERE id = ?",
                booking.get("id"));
        audit.event(null, "guest_pre_checkin", "booking",
                ((Number) booking.get("id")).longValue(), null);
        return message("Pre-check-in completed successfully");
    }

    @PostMapping("/api/guest-portal/auto-checkin/{token}")
    public Map<String, Object> autoCheckin(@PathVariable String token) {
        Map<String, Object> booking = portalBooking(token);
        if (!Boolean.TRUE.equals(booking.get("pre_checkin_completed"))) {
            throw ApiError.conflict("Complete pre-check-in before requesting self check-in");
        }
        jdbc.update("""
                UPDATE bookings SET status = 'checked_in', actual_check_in = NOW()
                WHERE id = ? AND status IN ('reserved','confirmed')
                """, booking.get("id"));
        Number roomId = (Number) booking.get("room_id");
        if (roomId != null) {
            jdbc.update("UPDATE rooms SET status = 'occupied' WHERE id = ?", roomId.longValue());
        }
        audit.event(null, "guest_self_checkin", "booking",
                ((Number) booking.get("id")).longValue(), null);
        return message("Check-in completed successfully");
    }

    @PostMapping("/api/guest-portal/booking/{token}/payments/bank-transfer")
    public Map<String, Object> bankTransfer(@PathVariable String token,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> booking = portalBooking(token);
        BigDecimal amount = dec(body.get("amount"));
        if (amount == null || amount.signum() <= 0) {
            throw ApiError.badRequest("Positive amount is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO payment_receipt_requests (booking_id, amount, method, reference_number,
                    status)
                VALUES (?, ?, 'bank_transfer', ?, 'pending')
                RETURNING id
                """, Long.class, booking.get("id"), amount, str(body, "reference_number"));
        audit.event(null, "bank_transfer_receipt_submitted", "payment_receipt_request", id, null);
        return message("Bank transfer receipt submitted for review");
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
        Map<String, Object> body = new LinkedHashMap<>();
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
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM ekyc_verifications WHERE id = ?", id);
        return rows.get(0);
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

    private BigDecimal dec(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            return new BigDecimal(s.trim());
        }
        return null;
    }
}
