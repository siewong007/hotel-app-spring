package com.hotelapp.rooms;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGate;
import com.hotelapp.core.web.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of routes/rooms.rs: room inventory CRUD and status management.
 */
@RestController
public class RoomsController {

    private final JdbcTemplate jdbc;
    private final PermissionGate gate;
    private final AuditWriter audit;

    public RoomsController(JdbcTemplate jdbc, PermissionGate gate, AuditWriter audit) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.audit = audit;
    }

    @GetMapping("/api/rooms")
    public Map<String, Object> listRooms(@org.springframework.web.bind.annotation.RequestParam
            Map<String, String> query) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:read");
        long page = Page.page(query);
        long pageSize = Page.pageSize(query);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM rooms", Long.class);
        List<Map<String, Object>> rooms = jdbc.queryForList("""
                SELECT r.*, rt.name AS room_type_name FROM rooms r
                LEFT JOIN room_types rt ON rt.id = r.room_type_id
                ORDER BY r.room_number LIMIT ? OFFSET ?
                """, pageSize, Page.offset(page, pageSize));
        return Page.of(rooms, total == null ? 0 : total, page, pageSize);
    }

    @PostMapping("/api/rooms")
    public Map<String, Object> createRoom(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:create");
        String roomNumber = str(body, "room_number");
        if (roomNumber == null || roomNumber.isBlank()) {
            throw ApiError.badRequest("Room number is required");
        }
        Number roomTypeId = num(body, "room_type_id");
        if (roomTypeId == null) {
            throw ApiError.badRequest("Room type is required");
        }
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO rooms (room_number, room_type_id, floor, status)
                    VALUES (?, ?, COALESCE(?, 1), COALESCE(?, 'available'))
                    RETURNING id
                    """, Long.class, roomNumber, roomTypeId.longValue(),
                    num(body, "floor"), str(body, "status"));
            audit.event(userId, "room_created", "room", id,
                    Map.of("room_number", roomNumber));
            return getRoomOrThrow(id);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiError.conflict("A room with this number already exists");
        }
    }

    @PatchMapping("/api/rooms/{id}")
    public Map<String, Object> updateRoom(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:update");
        getRoomOrThrow(id);
        applyPatch(jdbc, "rooms", "id", id, body, List.of("room_number", "room_type_id",
                "floor", "status", "notes"));
        audit.event(userId, "room_updated", "room", id, Map.of("fields", body.keySet()));
        return getRoomOrThrow(id);
    }

    @DeleteMapping("/api/rooms/{id}")
    public Map<String, Object> deleteRoom(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:delete");
        getRoomOrThrow(id);
        jdbc.update("DELETE FROM rooms WHERE id = ?", id);
        audit.event(userId, "room_deleted", "room", id, null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Room deleted successfully");
        return body;
    }

    @PutMapping("/api/rooms/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "rooms:update");
        getRoomOrThrow(id);
        String status = str(body, "status");
        if (status == null || status.isBlank()) {
            throw ApiError.badRequest("Status is required");
        }
        jdbc.update("UPDATE rooms SET status = ? WHERE id = ?", status, id);
        audit.event(userId, "room_status_changed", "room", id, Map.of("status", status));
        return getRoomOrThrow(id);
    }

    private Map<String, Object> getRoomOrThrow(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM rooms WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Room not found");
        }
        return rows.get(0);
    }

    static void applyPatch(JdbcTemplate jdbc, String table, String pkColumn, long id,
            Map<String, Object> body, List<String> allowedColumns) {
        var sets = new LinkedHashMap<String, Object>();
        for (String column : allowedColumns) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            String sql = "UPDATE " + table + " SET "
                    + String.join(", ", sets.keySet())
                    + " WHERE " + pkColumn + " = ?";
            jdbc.update(sql, sets.values().toArray());
        }
    }

    static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    static Number num(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                if (s.contains(".")) {
                    return Double.parseDouble(s);
                }
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
