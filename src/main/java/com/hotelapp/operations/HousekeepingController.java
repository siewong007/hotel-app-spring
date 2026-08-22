package com.hotelapp.operations;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGate;
import com.hotelapp.core.web.Page;
import static com.hotelapp.rates.RatesController.*;
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

@RestController
public class HousekeepingController {

    private final JdbcTemplate jdbc;
    private final PermissionGate gate;
    private final AuditWriter audit;

    public HousekeepingController(JdbcTemplate jdbc, PermissionGate gate, AuditWriter audit) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.audit = audit;
    }

    @GetMapping("/api/housekeeping/board")
    public Map<String, Object> board() {
        gate.check(CurrentUser.require().userId(), "housekeeping:read");
        List<Map<String, Object>> rooms = jdbc.queryForList("""
                SELECT r.id, r.room_number, r.floor, r.status,
                       rt.name AS room_type_name,
                       (SELECT ht.task_type FROM housekeeping_tasks ht
                        WHERE ht.room_id = r.id AND ht.status NOT IN ('completed','verified')
                        ORDER BY ht.created_at DESC LIMIT 1) AS active_task_type,
                       (SELECT ht.status FROM housekeeping_tasks ht
                        WHERE ht.room_id = r.id AND ht.status NOT IN ('completed','verified')
                        ORDER BY ht.created_at DESC LIMIT 1) AS active_task_status
                FROM rooms r LEFT JOIN room_types rt ON rt.id = r.room_type_id
                ORDER BY r.room_number
                """);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("rooms", rooms);
        body.put("summary", jdbc.queryForMap("""
                SELECT COUNT(*) AS total_rooms,
                       SUM(CASE WHEN status = 'available' THEN 1 ELSE 0 END) AS available,
                       SUM(CASE WHEN status = 'occupied' THEN 1 ELSE 0 END) AS occupied,
                       SUM(CASE WHEN status = 'cleaning' THEN 1 ELSE 0 END) AS cleaning,
                       SUM(CASE WHEN status = 'dirty' THEN 1 ELSE 0 END) AS dirty,
                       SUM(CASE WHEN status = 'maintenance' THEN 1 ELSE 0 END) AS maintenance
                FROM rooms
                """));
        return body;
    }

    @GetMapping("/api/housekeeping/tasks")
    public Map<String, Object> list(@RequestParam Map<String, String> q) {
        gate.check(CurrentUser.require().userId(), "housekeeping:read");
        long page = Page.page(q);
        long size = Page.pageSize(q);
        String status = q.get("status");
        String where = status == null || status.isBlank() ? "" : "WHERE ht.status = ?";
        Object[] baseArgs = where.isEmpty() ? new Object[] {} : new Object[] {status};
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM housekeeping_tasks ht " + where, Long.class, baseArgs);
        List<Map<String, Object>> data = jdbc.queryForList("""
                SELECT ht.*, r.room_number FROM housekeeping_tasks ht
                LEFT JOIN rooms r ON r.id = ht.room_id
                """ + where + " ORDER BY ht.created_at DESC LIMIT ? OFFSET ?",
                concatAll(baseArgs, size, Page.offset(page, size)));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/housekeeping/tasks")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "housekeeping:create");
        Number roomId = num(body, "room_id");
        String taskType = str(body, "task_type");
        if (roomId == null || taskType == null) {
            throw ApiError.badRequest("Room ID and task type are required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO housekeeping_tasks (room_id, task_type, priority, assigned_to,
                    scheduled_date, notes, status)
                VALUES (?, ?, COALESCE(?, 'normal'), ?, CAST(COALESCE(?,'2023-01-01') AS date), ?, 'pending')
                RETURNING id
                """, Long.class, roomId.longValue(), taskType, str(body, "priority"),
                num(body, "assigned_to"), str(body, "scheduled_date"), str(body, "notes"));
        audit.event(userId, "housekeeping_task_created", "housekeeping_task", id, null);
        return one(id);
    }

    @PatchMapping("/api/housekeeping/tasks/{id}")
    public Map<String, Object> update(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "housekeeping:update");
        one(id);
        var sets = new java.util.LinkedHashMap<String, Object>();
        for (String column : List.of("status", "priority", "assigned_to", "notes",
                "completion_notes")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (body.containsKey("status") && "completed".equalsIgnoreCase(str(body, "status"))) {
            sets.put("completed_at = NOW()", null);
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE housekeeping_tasks SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(userId, "housekeeping_task_updated", "housekeeping_task", id, null);
        return one(id);
    }

    @GetMapping("/api/maintenance")
    public Map<String, Object> listTickets(@RequestParam Map<String, String> q) {
        gate.check(CurrentUser.require().userId(), "maintenance:read");
        long page = Page.page(q);
        long size = Page.pageSize(q);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM maintenance_tickets", Long.class);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT * FROM maintenance_tickets ORDER BY created_at DESC LIMIT ? OFFSET ?",
                size, Page.offset(page, size));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/maintenance")
    public Map<String, Object> createTicket(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "maintenance:write");
        String title = str(body, "title");
        if (title == null) {
            throw ApiError.badRequest("Title is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO maintenance_tickets (title, description, room_id, priority, status, reported_by)
                VALUES (?, ?, ?, COALESCE(?, 'medium'), 'open', ?) RETURNING id
                """, Long.class, title, str(body, "description"), num(body, "room_id"),
                str(body, "priority"), userId);
        audit.event(userId, "maintenance_ticket_created", "maintenance_ticket", id, null);
        return oneTicket(id);
    }

    @GetMapping("/api/maintenance/{id}")
    public Map<String, Object> getTicket(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "maintenance:read");
        return oneTicket(id);
    }

    @PatchMapping("/api/maintenance/{id}")
    public Map<String, Object> updateTicket(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "maintenance:write");
        oneTicket(id);
        var sets = new java.util.LinkedHashMap<String, Object>();
        for (String column : List.of("title", "description", "priority", "status",
                "assigned_to", "resolution_notes")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE maintenance_tickets SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(userId, "maintenance_ticket_updated", "maintenance_ticket", id, null);
        return oneTicket(id);
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM housekeeping_tasks WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Task not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> oneTicket(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM maintenance_tickets WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Ticket not found");
        }
        return rows.get(0);
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
                return s.contains(".") ? (Number) Double.parseDouble(s)
                        : (Number) Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Object[] concatAll(Object[] base, Object... extra) {
        Object[] all = new Object[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }
}
