package com.hotelapp.gaps;

import java.math.BigDecimal;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Final inventory gaps: loyalty member surface, online inventory, audit
 * category counts, ekyc detail/document routes, guest-portal paypal/session
 * extras, room execute-change and per-room occupancy.
 */
@RestController
public class FinalGapsController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public FinalGapsController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @PostMapping("/api/loyalty/enroll")
    public Map<String, Object> enroll() {
        long userId = CurrentUser.require().userId();
        Long guestId = guestIdFor(userId);
        Long existing = jdbc.queryForObject(
                "SELECT id FROM loyalty_members WHERE guest_id = ?", Long.class, guestId);
        if (existing != null) {
            throw ApiError.conflict("Already enrolled in the loyalty program");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO loyalty_members (guest_id, member_number, points_balance,
                    lifetime_points, tier)
                VALUES (?, 'LM-' || ?::text, 0, 0, 'member') RETURNING id
                """, Long.class, guestId, guestId);
        audit.event(userId, "loyalty_enrolled", "loyalty_member", id, null);
        return one("loyalty_members", "id", id);
    }

    @GetMapping("/api/loyalty/me")
    public Map<String, Object> me() {
        long userId = CurrentUser.require().userId();
        Long guestId = guestIdFor(userId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT lm.*, g.nick_name AS guest_name FROM loyalty_members lm
                LEFT JOIN guests g ON g.id = lm.guest_id WHERE lm.guest_id = ?
                """, guestId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("No loyalty membership for this account");
        }
        return rows.get(0);
    }

    @GetMapping("/api/loyalty/me/activity")
    public List<Map<String, Object>> myActivity() {
        long userId = CurrentUser.require().userId();
        Long memberId = memberIdFor(userId);
        return jdbc.queryForList("""
                SELECT * FROM loyalty_transactions WHERE member_id = ?
                ORDER BY created_at DESC LIMIT 100
                """, memberId);
    }

    @GetMapping("/api/loyalty/rewards")
    public List<Map<String, Object>> memberRewards() {
        memberIdFor(CurrentUser.require().userId());
        return jdbc.queryForList("""
                SELECT * FROM loyalty_rewards
                WHERE is_active AND (valid_to IS NULL OR valid_to >= CURRENT_DATE)
                ORDER BY points_cost
                """);
    }

    @PostMapping("/api/loyalty/rewards/{id}/redeem")
    public Map<String, Object> redeemReward(@PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        Long memberId = memberIdFor(userId);
        Map<String, Object> reward = one("loyalty_rewards", "id", id);
        BigDecimal cost = dec(reward.get("points_cost"));
        Integer updated = jdbc.queryForObject("""
                UPDATE loyalty_members SET points_balance = points_balance - ?
                WHERE id = ? AND points_balance >= ? RETURNING points_balance
                """, Integer.class, cost, memberId, cost);
        if (updated == null) {
            throw ApiError.conflict("Insufficient points balance");
        }
        jdbc.update("""
                INSERT INTO loyalty_redemptions (member_id, reward_id, status, redeemed_at)
                VALUES (?, ?, 'pending', NOW())
                """, memberId, id);
        audit.event(userId, "loyalty_reward_redeemed", "loyalty_redemption", null,
                Map.of("reward_id", id));
        return message("Redemption request submitted successfully");
    }

    @PostMapping("/api/admin/loyalty/members/{id}/adjustments")
    public Map<String, Object> manualAdjustment(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("loyalty:manage"));
        BigDecimal points = dec(body.get("points"));
        if (points == null) {
            throw ApiError.badRequest("Points adjustment is required");
        }
        jdbc.update("""
                UPDATE loyalty_members SET points_balance =
                    GREATEST(COALESCE(points_balance,0) + ?, 0) WHERE id = ?
                """, points, id);
        jdbc.update("""
                INSERT INTO loyalty_transactions (member_id, transaction_type, points, description)
                VALUES (?, CASE WHEN ? < 0 THEN 'adjustment_deduct' ELSE 'adjustment_add' END,
                        ?, ?)
                """, id, points.intValue(), points, str(body, "reason"));
        audit.event(userId, "loyalty_manual_adjustment", "loyalty_member", id,
                Map.of("points", points));
        return message("Points adjusted successfully");
    }

    @GetMapping("/api/admin/loyalty/socket")
    public Map<String, Object> adminLoyaltySocket() {
        gateLoyaltyRead();
        return socketStatus();
    }

    @GetMapping("/api/guest-portal/me/loyalty/socket")
    public Map<String, Object> guestLoyaltySocket() {
        CurrentUser.require();
        return socketStatus();
    }

    private Map<String, Object> socketStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "connected");
        body.put("transport", "polling-fallback");
        return body;
    }

    @GetMapping("/api/admin/online-inventory")
    public List<Map<String, Object>> onlineInventory(
            @RequestParam(required = false) String stay_date) {
        PermissionGateHelper.checkAny(CurrentUser.require().userId(),
                List.of("rooms:update", "rooms:manage"));
        if (stay_date != null && !stay_date.isBlank()) {
            return jdbc.queryForList(
                    "SELECT * FROM online_inventory_allocations WHERE stay_date = CAST(? AS date)",
                    stay_date);
        }
        return jdbc.queryForList("SELECT * FROM online_inventory_allocations ORDER BY stay_date");
    }

    @PutMapping("/api/admin/online-inventory/{roomTypeId}/{stayDate}")
    public Map<String, Object> updateOnlineInventory(@PathVariable long roomTypeId,
            @PathVariable String stayDate, @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("rooms:update", "rooms:manage"));
        Number roomsSold = num(body, "rooms_sold");
        Number allotment = num(body, "allotment");
        int updated = jdbc.update("""
                UPDATE online_inventory_allocations SET rooms_sold = COALESCE(?, rooms_sold),
                    allotment = COALESCE(?, allotment), updated_at = NOW()
                WHERE room_type_id = ? AND stay_date = CAST(? AS date)
                """, roomsSold, allotment, roomTypeId, stayDate);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO online_inventory_allocations (room_type_id, stay_date, rooms_sold,
                        allotment)
                    VALUES (?, ?, COALESCE(?, 0), COALESCE(?, 0))
                    """, roomTypeId, stayDate, roomsSold, allotment);
        }
        audit.event(userId, "online_inventory_updated", "online_inventory_allocation", null,
                Map.of("room_type_id", roomTypeId, "stay_date", stayDate));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM online_inventory_allocations
                WHERE room_type_id = ? AND stay_date = CAST(? AS date)
                """, roomTypeId, stayDate);
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    @GetMapping("/api/audit-logs/category-counts")
    public Map<String, Object> categoryCounts() {
        PermissionGateHelper.checkAny(CurrentUser.require().userId(),
                List.of("audit:read", "audit:manage"));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT resource_type, COUNT(*) AS count FROM audit_logs
                GROUP BY resource_type ORDER BY count DESC
                """);
        Map<String, Object> body = new LinkedHashMap<>();
        rows.forEach(row -> body.put(String.valueOf(row.get("resource_type")), row.get("count")));
        return body;
    }

    @GetMapping("/api/audit-logs/db-statements")
    public List<Map<String, Object>> dbStatements() {
        PermissionGateHelper.checkAny(CurrentUser.require().userId(),
                List.of("audit:read", "audit:manage"));
        // The Rust implementation surfaces slow-statement logs from its log file;
        // the Spring port exposes pg_stat_statements when available.
        try {
            return jdbc.queryForList("""
                    SELECT queryid::text AS id, calls, round(total_exec_time::numeric, 2) AS total_ms,
                           left(query, 200) AS query
                    FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20
                    """);
        } catch (org.springframework.dao.DataAccessException e) {
            return List.of();
        }
    }

    @PostMapping("/api/ekyc/admin/applications")
    public Map<String, Object> createAdminApplication(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("ekyc:review", "ekyc:manage"));
        Number guestId = num(body, "guest_id");
        if (guestId == null) {
            throw ApiError.badRequest("Guest ID is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO ekyc_verifications (guest_id, status) VALUES (?, 'pending')
                RETURNING id
                """, Long.class, guestId.longValue());
        audit.event(userId, "ekyc_application_created", "ekyc_verification", id, null);
        return one("ekyc_verifications", "id", id);
    }

    @GetMapping("/api/ekyc/admin/applications/export")
    public List<Map<String, Object>> exportApplications() {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("ekyc:export", "ekyc:read",
                "ekyc:manage"));
        audit.event(userId, "ekyc_applications_exported", "ekyc_verification", null, null);
        return jdbc.queryForList("SELECT * FROM ekyc_verifications ORDER BY created_at DESC");
    }

    @GetMapping("/api/ekyc/admin/applications/{id}")
    public Map<String, Object> applicationDetail(@PathVariable long id) {
        PermissionGateHelper.checkAny(CurrentUser.require().userId(),
                List.of("ekyc:review", "ekyc:read", "ekyc:manage"));
        return one("ekyc_verifications", "id", id);
    }

    @GetMapping({"/api/ekyc/admin/applications/{id}/documents/{kind}",
                 "/api/ekyc/verifications/{id}/documents/{kind}"})
    public Map<String, Object> documents(@PathVariable long id, @PathVariable String kind) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("ekyc:download_documents",
                "ekyc:read", "ekyc:manage"));
        Map<String, Object> verification = one("ekyc_verifications", "id", id);
        List<Map<String, Object>> docs = jdbc.queryForList(
                "SELECT * FROM guest_documents WHERE document_type = ? AND guest_id = ?",
                kind, verification.get("guest_id"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("documents", docs);
        return body;
    }

    @PostMapping("/api/ekyc/admin/applications/{id}/reveal")
    public Map<String, Object> revealSensitive(@PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("ekyc:reveal", "ekyc:manage"));
        jdbc.update("""
                INSERT INTO ekyc_sensitive_reveals (verification_id, revealed_by, reason)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, id, userId, str(body, "reason"));
        audit.event(userId, "ekyc_sensitive_revealed", "ekyc_verification", id,
                Map.of("reason", str(body, "reason")));
        return one("ekyc_verifications", "id", id);
    }

    @PostMapping("/api/ekyc/self-checkin")
    public Map<String, Object> selfCheckin(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        String bookingNumber = str(body, "booking_number");
        if (bookingNumber == null) {
            throw ApiError.badRequest("Booking number is required");
        }
        Integer updated = jdbc.queryForObject("""
                UPDATE bookings b SET status = 'checked_in', actual_check_in = NOW()
                FROM users u
                WHERE u.id = ? AND u.guest_id = b.guest_id AND b.booking_number = ?
                  AND b.status IN ('reserved','confirmed')
                  AND EXISTS (SELECT 1 FROM ekyc_verifications ev
                              WHERE ev.guest_id = u.guest_id AND ev.status = 'approved')
                RETURNING b.id::text
                """, Integer.class, userId, bookingNumber);
        if (updated == null) {
            throw ApiError.conflict(
                    "Self check-in requires an approved eKYC and a reserved booking");
        }
        audit.event(userId, "ekyc_self_checkin_completed", "booking", null,
                Map.of("booking_number", bookingNumber));
        return message("Self check-in completed successfully");
    }

    @GetMapping("/api/ekyc/verifications")
    public List<Map<String, Object>> verifications() {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("ekyc:read", "ekyc:review",
                "ekyc:manage"));
        return jdbc.queryForList("SELECT * FROM ekyc_verifications ORDER BY created_at DESC");
    }

    @GetMapping("/api/ekyc/verifications/{id}")
    public Map<String, Object> verificationDetail(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("ekyc:read", "ekyc:review",
                "ekyc:manage"));
        return one("ekyc_verifications", "id", id);
    }

    @GetMapping("/api/guest-portal/me/summary")
    public Map<String, Object> portalMeSummary() {
        long userId = CurrentUser.require().userId();
        Long guestId = guestIdFor(userId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("bookings", jdbc.queryForList(
                "SELECT id, booking_number, status, check_in_date, check_out_date FROM bookings "
                        + "WHERE guest_id = ? ORDER BY created_at DESC", guestId));
        summary.put("transactions", jdbc.queryForList(
                "SELECT * FROM payments WHERE booking_id IN "
                        + "(SELECT id FROM bookings WHERE guest_id = ?)", guestId));
        summary.put("credits", jdbc.queryForList(
                "SELECT * FROM guest_complimentary_credits WHERE guest_id = ? AND credit_nights > 0",
                guestId));
        return summary;
    }

    @PostMapping("/api/rooms/{id}/execute-change")
    public Map<String, Object> executeRoomChange(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(userId, List.of("rooms:update", "bookings:update"));
        Number bookingId = num(body, "booking_id");
        Number newRoomId = num(body, "new_room_id");
        if (bookingId == null || newRoomId == null) {
            throw ApiError.badRequest("Booking ID and new room ID are required");
        }
        jdbc.update("UPDATE bookings SET room_id = ? WHERE id = ?", newRoomId.longValue(),
                bookingId.longValue());
        jdbc.update("""
                INSERT INTO booking_modifications (booking_id, modification_type, old_value,
                    new_value, modified_by)
                VALUES (?, 'room_change', CAST(? AS text), CAST(? AS text), ?)
                """, bookingId.longValue(), num(body, "old_room_id"),
                newRoomId.longValue(), userId);
        audit.event(userId, "room_change_executed", "booking", bookingId.longValue(),
                Map.of("new_room_id", newRoomId.longValue()));
        return message("Room change executed successfully");
    }

    @GetMapping("/api/rooms/{id}/occupancy")
    public Map<String, Object> roomOccupancy(@PathVariable long id) {
        CurrentUser.require();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id AS booking_id, b.booking_number, b.check_in_date, b.check_out_date,
                       b.status
                FROM bookings b WHERE b.room_id = ?
                  AND b.check_out_date >= CURRENT_DATE - INTERVAL '30 days'
                ORDER BY b.check_in_date DESC LIMIT 30
                """, id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("room_id", id);
        body.put("stays", rows);
        return body;
    }

    private void gateLoyaltyRead() {
        PermissionGateHelper.checkAny(CurrentUser.require().userId(),
                List.of("loyalty:read", "loyalty:manage"));
    }

    private Long guestIdFor(long userId) {
        Long guestId = jdbc.queryForObject(
                "SELECT guest_id FROM users WHERE id = ?", Long.class, userId);
        if (guestId == null) {
            throw ApiError.badRequest("This account has no linked guest profile");
        }
        return guestId;
    }

    private Long memberIdFor(long userId) {
        Long guestId = guestIdFor(userId);
        Long memberId = jdbc.queryForObject(
                "SELECT id FROM loyalty_members WHERE guest_id = ?", Long.class, guestId);
        if (memberId == null) {
            throw ApiError.notFound("No loyalty membership for this account");
        }
        return memberId;
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

    private Map<String, Object> one(String table, String pk, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE " + pk + " = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Resource not found");
        }
        return rows.get(0);
    }
}
