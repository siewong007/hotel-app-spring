package com.hotelapp.gaps;

import java.math.BigDecimal;

import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.core.security.RbacService;
import com.hotelapp.guests.GuestViews;
import com.hotelapp.payments.PaymentModels.PendingPaymentPage;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import com.hotelapp.payments.StaffPayments;
import com.hotelapp.payments.StaffPayments.ReceiptPayload;
import com.hotelapp.promotions.WelcomeVouchers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Remaining auth/profile/2FA/passkey, admin payment approvals, member loyalty,
 * guest credit extras and the ekyc/self-checkin surface.
 */
@RestController
public class AccountGapsController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final RbacService rbac;
    private final TransactionTemplate tx;
    private final StaffPayments payments;

    public AccountGapsController(JdbcTemplate jdbc, AuditWriter audit,
            RbacService rbac, TransactionTemplate tx, StaffPayments payments) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.rbac = rbac;
        this.tx = tx;
        this.payments = payments;
    }


    @GetMapping("/api/admin/payments/pending")
    public PendingPaymentPage pendingPayments(
            @RequestParam(required = false) Long page,
            @RequestParam(name = "per_page", required = false) Long perPage) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:read");
        long[] limitOffset = limitOffset(page, perPage);
        return payments.listPendingPayments(limitOffset[0], limitOffset[1]);
    }

    @GetMapping("/api/admin/payments/history")
    public PendingPaymentPage approvalHistory(
            @RequestParam(required = false) Long page,
            @RequestParam(name = "per_page", required = false) Long perPage) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:read");
        long[] limitOffset = limitOffset(page, perPage);
        return payments.listPaymentApprovalHistory(limitOffset[0], limitOffset[1]);
    }

    /** {@code PendingPaymentsQuery::limit_offset} — page >= 1, per_page 1..=100 (default 20). */
    static long[] limitOffset(Long page, Long perPage) {
        long pp = perPage == null ? 20 : Math.clamp(perPage, 1, 100);
        long p = page == null ? 1 : Math.max(page, 1);
        return new long[] {pp, (p - 1) * pp};
    }

    @PutMapping("/api/admin/payments/{id}/approve")
    public PaymentActionResponse approvePayment(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:approve");
        return payments.approvePayment(userId, id);
    }

    @PutMapping("/api/admin/payments/{id}/reject")
    public PaymentActionResponse rejectPayment(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:approve");
        String reason = body == null ? null : str(body, "reason");
        return payments.rejectPayment(userId, id, reason);
    }

    @PostMapping("/api/admin/payments/{id}/request-receipt")
    public Map<String, Object> requestReceipt(@PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:approve");
        String note = body == null ? null : str(body, "message");
        payments.requestPaymentReceipt(userId, id, note);
        return Map.of("requested", true);
    }

    @GetMapping("/api/admin/payments/{id}/receipt")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "payments:read");
        ReceiptPayload receipt = payments.loadPaymentReceipt(id);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(receipt.contentType());
        } catch (InvalidMediaTypeException e) {
            throw ApiError.internal("Stored receipt has an invalid content type.");
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=payment-receipt-" + id)
                .body(receipt.bytes());
    }

    @PostMapping("/api/guests/{id}/portal-account")
    public Map<String, Object> transferPortalAccount(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "guests:update");
        String username = str(body, "username");
        if (username == null || username.trim().isEmpty() || username.trim().length() > 50) {
            throw ApiError.badRequest("A valid guest portal username is required");
        }
        username = username.trim();
        final String portalUsername = username;
        Map<String, Object> transfer = tx.execute(status -> {
            Boolean targetActive = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM guests"
                            + " WHERE id = ? AND deleted_at IS NULL AND is_active = true)",
                    Boolean.class, id);
            if (!Boolean.TRUE.equals(targetActive)) {
                throw ApiError.badRequest(
                        "Guest portal accounts can only be assigned to an active guest");
            }
            List<Map<String, Object>> account = jdbc.queryForList(
                    "SELECT id, guest_id FROM users"
                            + " WHERE username = ? AND user_type = 'guest'"
                            + " AND is_active = true AND deleted_at IS NULL",
                    portalUsername);
            if (account.isEmpty()) {
                throw ApiError.notFound("Active guest portal account not found");
            }
            long portalUserId = ((Number) account.get(0).get("id")).longValue();
            Object previousGuestId = account.get(0).get("guest_id");
            Boolean another = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM users"
                            + " WHERE guest_id = ? AND id <> ? AND user_type = 'guest'"
                            + " AND is_active = true AND deleted_at IS NULL)",
                    Boolean.class, id, portalUserId);
            if (Boolean.TRUE.equals(another)) {
                throw ApiError.conflict(
                        "This guest already has an active guest portal account");
            }
            if (previousGuestId != null) {
                jdbc.update("DELETE FROM guest_portal_sessions WHERE guest_id = ?",
                        previousGuestId);
            }
            jdbc.update("UPDATE users SET guest_id = ?,"
                            + " updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    id, portalUserId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("portal_user_id", portalUserId);
            out.put("previous_guest_id", previousGuestId);
            return out;
        });
        WelcomeVouchers.issue(jdbc, tx, audit, id);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("portal_user_id", transfer.get("portal_user_id"));
        details.put("portal_username", portalUsername);
        details.put("previous_guest_id", transfer.get("previous_guest_id"));
        audit.event(userId, "guest_portal_account_transferred", "guest", id, details);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Guest portal account transferred successfully");
        out.put("guest_id", id);
        out.put("username", portalUsername);
        return out;
    }

    @PostMapping("/api/guests/{id}/tourism-from-last-check-in")
    public Map<String, Object> tourismFromLastCheckIn(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "guests:update");
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM guests WHERE id = ? AND deleted_at IS NULL)",
                Boolean.class, id);
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiError.notFound("Guest not found");
        }
        List<Map<String, Object>> signal = jdbc.queryForList("""
                WITH payment_totals AS (
                    SELECT booking_id,
                        COALESCE(SUM(CASE WHEN status = 'completed' THEN amount ELSE 0 END), 0)
                        - COALESCE(SUM(CASE
                            WHEN status = 'refunded' THEN COALESCE(refund_amount, amount)
                            ELSE COALESCE(refund_amount, 0)
                        END), 0) AS net_paid_amount
                    FROM payments
                    GROUP BY booking_id
                )
                SELECT b.id AS booking_id, b.booking_number,
                       b.check_in_date, b.check_out_date,
                       COALESCE(b.tourism_tax_amount, 0) AS tourism_tax_amount,
                       GREATEST(COALESCE(p.net_paid_amount, 0), 0) AS net_paid_amount
                FROM bookings b
                LEFT JOIN payment_totals p ON p.booking_id = b.id
                WHERE b.guest_id = ?
                  AND b.status IN ('checked_in', 'auto_checked_in', 'checked_out', 'completed')
                ORDER BY COALESCE(b.actual_check_in, b.created_at) DESC,
                         b.check_in_date DESC, b.id DESC
                LIMIT 1
                """, id);
        if (signal.isEmpty()) {
            throw ApiError.badRequest(
                    "No checked-in booking was found for this guest."
                            + " Check the guest in first, then try again.");
        }
        Map<String, Object> row = signal.get(0);
        BigDecimal taxAmount = (BigDecimal) row.get("tourism_tax_amount");
        BigDecimal netPaid = (BigDecimal) row.get("net_paid_amount");
        boolean paidTourismTax = taxAmount.signum() > 0
                && netPaid.compareTo(taxAmount) >= 0;
        String inferredType = paidTourismTax ? "foreign" : "local";
        Map<String, Object> guest = GuestViews.json(jdbc.queryForList(
                "UPDATE guests SET tourism_type = ?,"
                        + " updated_at = CURRENT_TIMESTAMP"
                        + " WHERE id = ? AND deleted_at IS NULL"
                        + " RETURNING " + GuestViews.COLUMNS,
                inferredType, id).get(0));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("booking_id", row.get("booking_id"));
        source.put("booking_number", row.get("booking_number"));
        source.put("check_in_date", row.get("check_in_date"));
        source.put("check_out_date", row.get("check_out_date"));
        source.put("tourism_tax_amount", taxAmount);
        source.put("net_paid_amount", netPaid);
        source.put("paid_tourism_tax", paidTourismTax);
        source.put("inferred_tourism_type", inferredType);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tourism_type", guest.get("tourism_type"));
        details.put("booking_id", row.get("booking_id"));
        details.put("booking_number", row.get("booking_number"));
        details.put("tourism_tax_amount", String.valueOf(taxAmount));
        details.put("net_paid_amount", String.valueOf(netPaid));
        details.put("paid_tourism_tax", paidTourismTax);
        audit.event(userId, "guest_tourism_type_inferred", "guest", id, details);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("guest", guest);
        out.put("source", source);
        return out;
    }

    @GetMapping("/api/guests/{id}/credits")
    public Map<String, Object> guestCredits(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        Boolean hasAccess = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM user_guests"
                        + " WHERE user_id = ? AND guest_id = ?)",
                Boolean.class, userId, id);
        boolean hasPermission = rbac.hasPermission(userId, "guests:read");
        if (!Boolean.TRUE.equals(hasAccess) && !hasPermission) {
            throw ApiError.unauthorized(
                    "You don't have access to this guest's credits");
        }
        List<Map<String, Object>> info = jdbc.queryForList(
                "SELECT id, nick_name FROM guests WHERE id = ? AND deleted_at IS NULL", id);
        if (info.isEmpty()) {
            throw ApiError.notFound("Guest not found");
        }
        List<Map<String, Object>> credits = jdbc.queryForList("""
                SELECT gcc.id, gcc.guest_id, gcc.room_type_id,
                       rt.name AS room_type_name, rt.code AS room_type_code,
                       gcc.nights_available, gcc.created_at, gcc.updated_at
                FROM guest_complimentary_credits gcc
                INNER JOIN room_types rt ON gcc.room_type_id = rt.id
                WHERE gcc.guest_id = ? AND gcc.nights_available > 0
                ORDER BY rt.name
                """, id);
        int totalNights = 0;
        for (Map<String, Object> credit : credits) {
            totalNights += ((Number) credit.get("nights_available")).intValue();
        }
        Integer legacyTotal = jdbc.queryForObject(
                "SELECT COALESCE(complimentary_nights_credit, 0)"
                        + " FROM guests WHERE id = ?",
                Integer.class, id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("guest_id", info.get(0).get("id"));
        out.put("guest_name", info.get(0).get("nick_name"));
        out.put("total_nights", totalNights);
        out.put("legacy_total_nights", legacyTotal == null ? 0 : legacyTotal);
        out.put("credits_by_room_type", credits);
        return out;
    }

    @GetMapping("/api/guests/my-guests-with-credits")
    public List<Map<String, Object>> myGuestsWithCredits() {
        long userId = CurrentUser.require().userId();
        boolean hasGuestAccess = rbac.hasPermission(userId, "guests:read")
                || rbac.hasPermission(userId, "guests:manage");
        List<Map<String, Object>> guests;
        if (hasGuestAccess) {
            guests = jdbc.queryForList("""
                    SELECT DISTINCT g.id, g.nick_name, g.email,
                           COALESCE(g.complimentary_nights_credit, 0) AS legacy_credits
                    FROM guests g
                    WHERE g.deleted_at IS NULL
                      AND EXISTS (SELECT 1 FROM guest_complimentary_credits gcc
                          WHERE gcc.guest_id = g.id AND gcc.nights_available > 0)
                    ORDER BY g.nick_name
                    """);
        } else {
            guests = jdbc.queryForList("""
                    SELECT DISTINCT g.id, g.nick_name, g.email,
                           COALESCE(g.complimentary_nights_credit, 0) AS legacy_credits
                    FROM guests g
                    INNER JOIN user_guests ug ON g.id = ug.guest_id
                    WHERE ug.user_id = ? AND g.deleted_at IS NULL
                    ORDER BY g.nick_name
                    """, userId);
        }
        List<Map<String, Object>> result = new ArrayList<>(guests.size());
        for (Map<String, Object> guest : guests) {
            long guestId = ((Number) guest.get("id")).longValue();
            List<Map<String, Object>> credits = jdbc.queryForList("""
                    SELECT gcc.room_type_id, rt.name AS room_type_name,
                           rt.code AS room_type_code, gcc.nights_available
                    FROM guest_complimentary_credits gcc
                    INNER JOIN room_types rt ON gcc.room_type_id = rt.id
                    WHERE gcc.guest_id = ? AND gcc.nights_available > 0
                    ORDER BY rt.name
                    """, guestId);
            int totalCredits = 0;
            for (Map<String, Object> credit : credits) {
                totalCredits += ((Number) credit.get("nights_available")).intValue();
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", guest.get("id"));
            row.put("nick_name", guest.get("nick_name"));
            row.put("email", guest.get("email"));
            row.put("legacy_complimentary_nights_credit", guest.get("legacy_credits"));
            row.put("total_complimentary_credits", totalCredits);
            row.put("credits_by_room_type", credits);
            result.add(row);
        }
        return result;
    }

    @PostMapping("/api/system/process-checkins")
    public Map<String, Object> processCheckins() {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("settings:manage", "night_audit:run"));
        int processed = jdbc.update("""
                UPDATE bookings SET status = 'checked_in', actual_check_in = NOW()
                WHERE status IN ('reserved','confirmed') AND check_in_date <= CURRENT_DATE
                  AND pre_checkin_completed = true
                  AND NOT EXISTS (
                      SELECT 1 FROM ekyc_verifications ev
                      JOIN users u ON u.guest_id = ev.guest_id
                      WHERE u.id = bookings.created_by AND ev.status = 'pending')
                """);
        audit.event(userId, "auto_checkins_processed", "booking", null,
                Map.of("processed", processed));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processed", processed);
        return body;
    }

}
