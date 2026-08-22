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
 * Remaining inventory endpoints for rooms/rates/booking-channels/night-audit
 * and the data-transfer GET surface.
 */
@RestController
public class InventoryGapsController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public InventoryGapsController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/rooms/available")
    public List<Map<String, Object>> available(@RequestParam Map<String, String> q) {
        gate(userId(), "rooms:read");
        String in = q.getOrDefault("check_in_date", LocalDate().toString());
        String out = q.getOrDefault("check_out_date", LocalDate().plusDays(1).toString());
        return jdbc.queryForList("""
                SELECT r.*, rt.name AS room_type_name, rt.base_price FROM rooms r
                LEFT JOIN room_types rt ON rt.id = r.room_type_id
                WHERE r.status IN ('available','cleaning')
                  AND NOT EXISTS (
                      SELECT 1 FROM bookings b WHERE b.room_id = r.id
                        AND b.status NOT IN ('cancelled','void')
                        AND b.check_in_date < CAST(? AS date)
                        AND b.check_out_date > CAST(? AS date))
                ORDER BY r.room_number
                """, out, in);
    }

    @GetMapping("/api/rooms/occupancy")
    public Map<String, Object> occupancyAll() {
        gate(userId(), "rooms:read");
        return summary();
    }

    @GetMapping("/api/rooms/occupancy/summary")
    public Map<String, Object> summary() {
        gate(userId(), "rooms:read");
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total_rooms,
                       SUM(CASE WHEN status='occupied' THEN 1 ELSE 0 END) AS occupied,
                       SUM(CASE WHEN status='reserved' THEN 1 ELSE 0 END) AS reserved,
                       SUM(CASE WHEN status='available' THEN 1 ELSE 0 END) AS available,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE status='occupied')
                             / GREATEST(COUNT(*),1), 2) AS occupancy_rate
                FROM rooms
                """);
    }

    @GetMapping("/api/rooms/occupancy/by-type")
    public List<Map<String, Object>> occupancyByType() {
        gate(userId(), "rooms:read");
        return jdbc.queryForList("""
                SELECT rt.id, rt.name, COUNT(r.id) AS total_rooms,
                       SUM(CASE WHEN r.status='occupied' THEN 1 ELSE 0 END) AS occupied
                FROM room_types rt LEFT JOIN rooms r ON r.room_type_id = rt.id
                GROUP BY rt.id, rt.name ORDER BY rt.name
                """);
    }

    @GetMapping("/api/rooms/with-occupancy")
    public List<Map<String, Object>> withOccupancy() {
        gate(userId(), "rooms:read");
        return jdbc.queryForList("""
                SELECT r.*, (b.booking_number) AS current_booking_number
                FROM rooms r
                LEFT JOIN bookings b ON b.room_id = r.id AND b.status = 'checked_in'
                ORDER BY r.room_number
                """);
    }

    @PostMapping("/api/rooms/sync-statuses")
    public Map<String, Object> syncStatuses() {
        long userId = userId();
        int updated = jdbc.update("""
                UPDATE rooms SET status = 'occupied'
                WHERE status <> 'maintenance' AND EXISTS (
                    SELECT 1 FROM bookings b WHERE b.room_id = rooms.id
                      AND b.status = 'checked_in')
                """);
        jdbc.update("""
                UPDATE rooms SET status = 'available'
                WHERE status = 'occupied' AND NOT EXISTS (
                    SELECT 1 FROM bookings b WHERE b.room_id = rooms.id
                      AND b.status = 'checked_in')
                """);
        audit.event(userId, "room_statuses_synced", "room", null, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", updated);
        return body;
    }

    @GetMapping("/api/rooms/{id}/detailed")
    public Map<String, Object> detailed(@PathVariable long id) {
        gate(userId(), "rooms:read");
        Map<String, Object> room = oneRoom(id);
        room.put("active_booking", jdbc.queryForList(
                "SELECT id, booking_number, guest_name, check_out_date FROM bookings "
                        + "WHERE room_id = ? AND status = 'checked_in'", id));
        room.put("open_tasks", jdbc.queryForList(
                "SELECT id, task_type, status FROM housekeeping_tasks "
                        + "WHERE room_id = ? AND status NOT IN ('completed','verified')", id));
        return room;
    }

    @PostMapping("/api/rooms/{id}/end-cleaning")
    public Map<String, Object> endCleaning(@PathVariable long id) {
        long uid = userId();
        oneRoom(id);
        jdbc.update("UPDATE housekeeping_tasks SET status = 'completed', completed_at = NOW() "
                + "WHERE room_id = ? AND task_type = 'cleaning' AND status <> 'completed'", id);
        jdbc.update("UPDATE rooms SET status = 'available' WHERE id = ?", id);
        audit.event(uid, "room_cleaning_ended", "room", id, null);
        return message("Cleaning ended successfully");
    }

    @PostMapping("/api/rooms/{id}/end-maintenance")
    public Map<String, Object> endMaintenance(@PathVariable long id) {
        long uid = userId();
        oneRoom(id);
        jdbc.update("UPDATE rooms SET status = 'available' WHERE id = ?", id);
        jdbc.update("UPDATE maintenance_tickets SET status = 'resolved', resolved_at = NOW() "
                + "WHERE room_id = ? AND status <> 'resolved'", id);
        audit.event(uid, "room_maintenance_ended", "room", id, null);
        return message("Maintenance ended successfully");
    }

    @PostMapping("/api/rooms/{id}/events")
    public Map<String, Object> roomEvent(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long uid = userId();
        oneRoom(id);
        String eventType = str(body, "event_type");
        if (eventType == null) {
            throw ApiError.badRequest("Event type is required");
        }
        jdbc.update("""
                INSERT INTO room_status_transitions (from_status, to_status, changed_by)
                VALUES (COALESCE((SELECT status FROM rooms WHERE id = ?), 'unknown'), ?, ?)
                """, id, eventType, uid);
        audit.event(uid, "room_event", "room", id, Map.of("event_type", eventType));
        return message("Room event recorded successfully");
    }

    @GetMapping("/api/rooms/{id}/history")
    public List<Map<String, Object>> roomHistory(@PathVariable long id) {
        gate(userId(), "rooms:read");
        oneRoom(id);
        return jdbc.queryForList(
                "SELECT * FROM room_status_transitions ORDER BY created_at DESC LIMIT 50", id);
    }

    @GetMapping("/api/rooms/change-history")
    public List<Map<String, Object>> changeHistory() {
        gate(userId(), "rooms:read");
        return jdbc.queryForList(
                "SELECT * FROM booking_modifications ORDER BY created_at DESC LIMIT 100");
    }

    @GetMapping("/api/rooms/{roomType}/reviews")
    public List<Map<String, Object>> reviews(@PathVariable String roomType) {
        gate(userId(), "rooms:read");
        return jdbc.queryForList("""
                SELECT gr.* FROM guest_reviews gr JOIN rooms r ON r.id = gr.room_id
                JOIN room_types rt ON rt.id = r.room_type_id
                WHERE rt.code = ? OR CAST(rt.id AS text) = ?
                ORDER BY gr.created_at DESC LIMIT 100
                """, roomType, roomType);
    }

    @GetMapping("/api/room-types/all")
    public List<Map<String, Object>> allRoomTypes() {
        CurrentUser.require();
        return jdbc.queryForList("SELECT * FROM room_types ORDER BY sort_order, id");
    }

    @GetMapping("/api/rate-management/room-types")
    public List<Map<String, Object>> rateManagementRoomTypes() {
        PermissionGateHelper.check(userId(), "rooms:read");
        return jdbc.queryForList("SELECT * FROM room_types ORDER BY sort_order, id");
    }

    @GetMapping("/api/room-rates")
    public List<Map<String, Object>> roomRates() {
        gate(userId(), "rooms:read");
        return jdbc.queryForList("SELECT * FROM room_rates ORDER BY effective_from DESC");
    }

    @PostMapping("/api/room-rates")
    public Map<String, Object> createRoomRate(@RequestBody Map<String, Object> body) {
        long userId = userId();
        PermissionGateHelper.check(userId, "rooms:write");
        Long id = jdbc.queryForObject("""
                INSERT INTO room_rates (rate_plan_id, room_type_id, price, effective_from,
                    effective_to)
                VALUES (?, ?, ?, CAST(? AS date), CAST(? AS date)) RETURNING id
                """, Long.class, num(body, "rate_plan_id"), num(body, "room_type_id"),
                numD(body.get("price")), str(body, "effective_from"),
                str(body, "effective_to"));
        audit.event(userId, "room_rate_created", "room_rate", id, null);
        return one("room_rates", "id", id);
    }

    @GetMapping("/api/room-rates/by-plan/{ratePlanId}")
    public List<Map<String, Object>> ratesByPlan(@PathVariable long ratePlanId) {
        gate(userId(), "rooms:read");
        return jdbc.queryForList("SELECT * FROM room_rates WHERE rate_plan_id = ?", ratePlanId);
    }

    @GetMapping("/api/room-rates/{id}")
    public Map<String, Object> getRoomRate(@PathVariable long id) {
        gate(userId(), "rooms:read");
        return one("room_rates", "id", id);
    }

    @PatchMapping("/api/room-rates/{id}")
    public Map<String, Object> updateRoomRate(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long uid = userId();
        PermissionGateHelper.check(uid, "rooms:update");
        one("room_rates", "id", id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("price", "effective_from", "effective_to")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE room_rates SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(uid, "room_rate_updated", "room_rate", id, null);
        return one("room_rates", "id", id);
    }

    @DeleteMapping("/api/room-rates/{id}")
    public Map<String, Object> deleteRoomRate(@PathVariable long id) {
        long uid = userId();
        PermissionGateHelper.check(uid, "rooms:write");
        one("room_rates", "id", id);
        jdbc.update("DELETE FROM room_rates WHERE id = ?", id);
        audit.event(uid, "room_rate_deleted", "room_rate", id, null);
        return message("Room rate deleted successfully");
    }

    @GetMapping("/api/rate-plans/{id}/with-rates")
    public Map<String, Object> ratePlanWithRates(@PathVariable long id) {
        gate(userId(), "rooms:read");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM rate_plans WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Rate plan not found");
        }
        Map<String, Object> plan = rows.get(0);
        plan.put("rates", jdbc.queryForList(
                "SELECT * FROM room_rates WHERE rate_plan_id = ?", id));
        return plan;
    }

    @PutMapping("/api/booking-channels/{id}")
    public Map<String, Object> updateChannel(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long uid = userId();
        PermissionGateHelper.check(uid, "settings:update");
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("name", "channel_code", "commission_rate",
                "requires_ota_reference", "is_active")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE booking_channels SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(uid, "booking_channel_updated", "booking_channel", id, null);
        return one("booking_channels", "id", id);
    }

    @DeleteMapping("/api/booking-channels/{id}")
    public Map<String, Object> deactivateChannel(@PathVariable long id) {
        long uid = userId();
        PermissionGateHelper.check(uid, "settings:update");
        jdbc.update("UPDATE booking_channels SET is_active = false WHERE id = ?", id);
        audit.event(uid, "booking_channel_deactivated", "booking_channel", id, null);
        return message("Channel deactivated successfully");
    }

    @GetMapping("/api/complimentary/summary")
    public Map<String, Object> complimentarySummary() {
        gate(userId(), "bookings:read");
        return jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE is_complimentary) AS active_complimentary,
                       COALESCE(SUM(complimentary_nights) FILTER (
                           WHERE is_complimentary), 0) AS complimentary_nights,
                       (SELECT COUNT(*) FROM guest_complimentary_credits
                           WHERE credit_nights > 0) AS guests_with_credits,
                       (SELECT COALESCE(SUM(credit_nights),0) FROM
                           guest_complimentary_credits) AS total_credit_nights
                FROM bookings
                """);
    }

    @GetMapping("/api/bookings/{id}/posted")
    public Map<String, Object> posted(@PathVariable long id) {
        gate(userId(), "bookings:read");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT posted_date FROM bookings WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Booking not found");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("posted", rows.get(0).get("posted_date") != null);
        body.put("posted_date", rows.get(0).get("posted_date"));
        return body;
    }

    @GetMapping("/api/night-audit")
    public List<Map<String, Object>> nightAudits() {
        PermissionGateHelper.check(userId(), "night_audit:run");
        return jdbc.queryForList("SELECT * FROM night_audit_runs ORDER BY audit_date DESC");
    }

    @GetMapping("/api/night-audit/{id}")
    public Map<String, Object> nightAudit(@PathVariable long id) {
        PermissionGateHelper.check(userId(), "night_audit:run");
        return one("night_audit_runs", "id", id);
    }

    @GetMapping("/api/night-audit/{id}/details")
    public Map<String, Object> nightAuditDetails(@PathVariable long id) {
        PermissionGateHelper.check(userId(), "night_audit:run");
        Map<String, Object> run = one("night_audit_runs", "id", id);
        run.put("details", jdbc.queryForList(
                "SELECT * FROM night_audit_details WHERE run_id = ?", id));
        return run;
    }

    @GetMapping("/api/data-transfer/export/preview")
    public Map<String, Object> exportPreview() {
        long uid = userId();
        PermissionGateHelper.checkAny(uid, List.of("data_transfer:export",
                "data_transfer:manage", "settings:manage"));
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("rooms", count("rooms"));
        counts.put("guests", count("guests"));
        counts.put("bookings", count("bookings"));
        counts.put("payments", count("payments"));
        return counts;
    }

    @GetMapping("/api/data-transfer/export")
    public Map<String, Object> exportGet() {
        long uid = userId();
        PermissionGateHelper.checkAny(uid, List.of("data_transfer:export",
                "data_transfer:manage", "settings:manage"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("preview_only", true);
        body.put("hint", "Use POST /api/data-transfer/export for the full bundle");
        return body;
    }

    private void gate(long userId, String permission) {
        PermissionGateHelper.check(userId, permission);
    }

    private long userId() {
        return CurrentUser.require().userId();
    }

    static java.time.LocalDate LocalDate() {
        return java.time.LocalDate.now();
    }

    private Map<String, Object> one(String table, String pk, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE " + pk + " = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Resource not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> oneRoom(long id) {
        return one("rooms", "id", id);
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    static BigDecimal numD(Object value) {
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            return new BigDecimal(s.trim());
        }
        return null;
    }
}
