package com.hotelapp.rates;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGate;
import com.hotelapp.core.web.Page;
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

@RestController
public class RatesController {

    private final JdbcTemplate jdbc;
    private final PermissionGate gate;
    private final AuditWriter audit;

    public RatesController(JdbcTemplate jdbc, PermissionGate gate, AuditWriter audit) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.audit = audit;
    }

    @GetMapping("/api/room-types")
    public Map<String, Object> listRoomTypes(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:read");
        long page = Page.page(q);
        long size = Page.pageSize(q);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM room_types", Long.class);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT * FROM room_types ORDER BY sort_order, id LIMIT ? OFFSET ?",
                size, Page.offset(page, size));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/room-types")
    public Map<String, Object> createRoomType(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:create");
        String name = str(body, "name");
        String code = str(body, "code");
        if (name == null || code == null) {
            throw ApiError.badRequest("Name and code are required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO room_types (name, code, description, max_occupancy, base_price,
                    size_sqm, bed_type, bed_count, allows_extra_bed, max_extra_beds,
                    extra_bed_charge, sort_order)
                VALUES (?, ?, ?, COALESCE(?, 2), COALESCE(?, 0), ?, ?, ?, ?, ?, ?, COALESCE(?, 0))
                RETURNING id
                """, Long.class, name, code, str(body, "description"),
                num(body, "max_occupancy"), num(body, "base_price"), num(body, "size_sqm"),
                str(body, "bed_type"), num(body, "bed_count"),
                body.get("allows_extra_bed"), num(body, "max_extra_beds"),
                num(body, "extra_bed_charge"), num(body, "sort_order"));
        audit.event(userId, "room_type_created", "room_type", id, null);
        return one("room_types", "id", id);
    }

    @GetMapping("/api/room-types/{id}")
    public Map<String, Object> getRoomType(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "rooms:read");
        return one("room_types", "id", id);
    }

    @PatchMapping("/api/room-types/{id}")
    @PutMapping("/api/room-types/{id}")
    public Map<String, Object> updateRoomType(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:update");
        one("room_types", "id", id);
        patch(jdbc, "room_types", "id", id, body, List.of("name", "code", "description",
                "max_occupancy", "base_price", "size_sqm", "bed_type", "bed_count",
                "allows_extra_bed", "max_extra_beds", "extra_bed_charge", "sort_order"));
        audit.event(userId, "room_type_updated", "room_type", id, null);
        return one("room_types", "id", id);
    }

    @DeleteMapping("/api/room-types/{id}")
    public Map<String, Object> deleteRoomType(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:delete");
        one("room_types", "id", id);
        try {
            jdbc.update("DELETE FROM room_types WHERE id = ?", id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiError.conflict("Room type is in use by rooms or bookings");
        }
        audit.event(userId, "room_type_deleted", "room_type", id, null);
        return message("Room type deleted successfully");
    }

    @GetMapping("/api/rate-plans")
    public Map<String, Object> listRatePlans(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:read");
        long page = Page.page(q);
        long size = Page.pageSize(q);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM rate_plans", Long.class);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT * FROM rate_plans ORDER BY priority DESC, id LIMIT ? OFFSET ?",
                size, Page.offset(page, size));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/rate-plans")
    public Map<String, Object> createRatePlan(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:create");
        String name = str(body, "name");
        String code = str(body, "code");
        if (name == null || code == null) {
            throw ApiError.badRequest("Name and code are required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO rate_plans (name, code, description, plan_type, adjustment_type,
                    adjustment_value, valid_from, valid_to, is_active, priority)
                VALUES (?, ?, ?, COALESCE(?,'standard'), COALESCE(?,'override'), ?,
                        COALESCE(?,'2023-01-01'), COALESCE(?,'2026-12-31'),
                        COALESCE(?, true), COALESCE(?, 50))
                RETURNING id
                """, Long.class, name, code, str(body, "description"), str(body, "plan_type"),
                str(body, "adjustment_type"), numD(body, "adjustment_value"),
                str(body, "valid_from"), str(body, "valid_to"),
                body.getOrDefault("is_active", true), num(body, "priority"));
        audit.event(userId, "rate_plan_created", "rate_plan", id, null);
        return one("rate_plans", "id", id);
    }

    @GetMapping("/api/rate-plans/{id}")
    public Map<String, Object> getRatePlan(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "rooms:read");
        return one("rate_plans", "id", id);
    }

    @PatchMapping("/api/rate-plans/{id}")
    @PutMapping("/api/rate-plans/{id}")
    public Map<String, Object> updateRatePlan(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:update");
        one("rate_plans", "id", id);
        patch(jdbc, "rate_plans", "id", id, body, List.of("name", "code", "description",
                "plan_type", "adjustment_type", "adjustment_value", "valid_from", "valid_to",
                "is_active", "priority"));
        audit.event(userId, "rate_plan_updated", "rate_plan", id, null);
        return one("rate_plans", "id", id);
    }

    @DeleteMapping("/api/rate-plans/{id}")
    public Map<String, Object> deleteRatePlan(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:delete");
        one("rate_plans", "id", id);
        jdbc.update("DELETE FROM rate_plans WHERE id = ?", id);
        audit.event(userId, "rate_plan_deleted", "rate_plan", id, null);
        return message("Rate plan deleted successfully");
    }

    @GetMapping("/api/room-rates/applicable")
    public List<Map<String, Object>> applicableRates(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:read");
        String date = q.getOrDefault("date", java.time.LocalDate.now().toString());
        String roomTypeId = q.get("room_type_id");
        String sql = """
                SELECT rr.*, rp.code AS rate_plan_code, rp.name AS rate_plan_name,
                       rt.code AS room_type_code
                FROM room_rates rr
                JOIN rate_plans rp ON rp.id = rr.rate_plan_id AND rp.is_active
                JOIN room_types rt ON rt.id = rr.room_type_id
                WHERE effective_from <= CAST(? AS date)
                  AND (effective_to IS NULL OR effective_to >= CAST(? AS date))
                """ + (roomTypeId != null ? " AND rr.room_type_id = ?" : "")
                + " ORDER BY rp.priority DESC";
        return roomTypeId == null
                ? jdbc.queryForList(sql, date, date)
                : jdbc.queryForList(sql, date, date, Long.parseLong(roomTypeId));
    }

    @GetMapping("/api/booking-channels")
    public List<Map<String, Object>> listChannels() {
        gate.check(CurrentUser.require().userId(), "bookings:read");
        return jdbc.queryForList(
                "SELECT * FROM booking_channels WHERE is_active ORDER BY display_order, name");
    }

    @PostMapping("/api/booking-channels")
    public Map<String, Object> createChannel(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "settings:manage");
        String name = str(body, "name");
        if (name == null) {
            throw ApiError.badRequest("Name is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO booking_channels (name, channel_code, commission_rate, requires_ota_reference, is_active)
                VALUES (?, ?, ?, ?, true) RETURNING id
                """, Long.class, name, str(body, "channel_code") != null
                        ? str(body, "channel_code") : name.toLowerCase(),
                numD(body, "commission_rate"), body.getOrDefault("requires_ota_reference", false));
        audit.event(userId, "booking_channel_created", "booking_channel", id, null);
        return one("booking_channels", "id", id);
    }

    @GetMapping("/api/rate-codes")
    public List<Map<String, Object>> rateCodes() {
        gate.check(CurrentUser.require().userId(), "bookings:read");
        return jdbc.queryForList(
                "SELECT id, code, name, plan_type, adjustment_type, adjustment_value "
                        + "FROM rate_plans WHERE is_active ORDER BY code");
    }

    @GetMapping("/api/market-codes")
    public List<Map<String, Object>> marketCodes() {
        gate.check(CurrentUser.require().userId(), "bookings:read");
        return jdbc.queryForList("""
                SELECT DISTINCT market_code AS code, market_code AS description
                FROM bookings WHERE market_code IS NOT NULL ORDER BY code
                """);
    }

    private Map<String, Object> one(String table, String pk, Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE " + pk + " = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Resource not found");
        }
        return rows.get(0);
    }

    public static void patch(JdbcTemplate jdbc, String table, String pk, long id,
            Map<String, Object> body, List<String> allowed) {
        var sets = new LinkedHashMap<String, Object>();
        for (String column : allowed) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE " + table + " SET " + String.join(", ", sets.keySet())
                    + " WHERE " + pk + " = ?", sets.values().toArray());
        }
    }

    public static Map<String, Object> message(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", text);
        return body;
    }

    public static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Number num(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return s.contains(".") ? (Number) Double.parseDouble(s)
                        : (Number) Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static java.math.BigDecimal numD(Map<String, Object> body, String key) {
        Number n = num(body, key);
        return n == null ? null : new java.math.BigDecimal(n.toString());
    }
}
