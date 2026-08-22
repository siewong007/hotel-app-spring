package com.hotelapp.guests;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGate;
import com.hotelapp.core.web.Page;
import static com.hotelapp.rates.RatesController.*;
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
public class GuestsController {

    private final JdbcTemplate jdbc;
    private final PermissionGate gate;
    private final AuditWriter audit;

    public GuestsController(JdbcTemplate jdbc, PermissionGate gate, AuditWriter audit) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.audit = audit;
    }

    @GetMapping("/api/guests")
    public Map<String, Object> list(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:read");
        long page = Page.page(q);
        long size = Page.pageSize(q);
        String search = q.get("search");
        String where = search == null || search.isBlank() ? ""
                : "WHERE (full_name ILIKE ? OR email ILIKE ? OR phone ILIKE ?"
                        + " OR first_name ILIKE ? OR last_name ILIKE ?)";
        Object[] args = search == null || search.isBlank()
                ? new Object[] {}
                : new Object[] {like(search), like(search), like(search), like(search),
                        like(search)};
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guests " + where, Long.class, args);
        List<Map<String, Object>> data = jdbc.queryForList(
                "SELECT * FROM guests " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                concat(args, size, Page.offset(page, size)));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/guests")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:create");
        String firstName = str(body, "first_name");
        if (firstName == null && str(body, "full_name") == null) {
            throw ApiError.badRequest("Guest name is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO guests (first_name, last_name, full_name, email, phone, nationality,
                    id_type, id_number, address_line_1, city, country, notes,
                    date_of_birth, vip_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS date), ?)
                RETURNING id
                """, Long.class,
                firstName, str(body, "last_name"),
                str(body, "full_name") != null ? str(body, "full_name")
                        : joinName(firstName, str(body, "last_name")),
                str(body, "email"), str(body, "phone"), str(body, "nationality"),
                str(body, "id_type"), str(body, "id_number"),
                str(body, "address_line_1"), str(body, "city"), str(body, "country"),
                str(body, "notes"), str(body, "date_of_birth"), str(body, "vip_status"));
        audit.event(userId, "guest_created", "guest", id, null);
        return one(id);
    }

    @GetMapping("/api/guests/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "guests:read");
        return one(id);
    }

    @PatchMapping("/api/guests/{id}")
    public Map<String, Object> update(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:update");
        one(id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("first_name", "last_name", "full_name", "email", "phone",
                "nationality", "id_type", "id_number", "address_line_1", "city", "country",
                "notes", "vip_status", "is_blacklisted", "blacklist_reason")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE guests SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(userId, "guest_updated", "guest", id, null);
        return one(id);
    }

    @DeleteMapping("/api/guests/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:delete");
        one(id);
        jdbc.update("UPDATE guests SET deleted_at = NOW(), is_active = false WHERE id = ?", id);
        audit.event(userId, "guest_deleted", "guest", id, null);
        return message("Guest deleted successfully");
    }

    @GetMapping("/api/guests/{id}/bookings")
    public List<Map<String, Object>> guestBookings(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "guests:read");
        one(id);
        return jdbc.queryForList("""
                SELECT b.* FROM bookings b WHERE b.guest_id = ?
                ORDER BY b.check_in_date DESC LIMIT 100
                """, id);
    }

    @GetMapping("/api/guests/{id}/profile")
    public Map<String, Object> profile(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "guests:read");
        Map<String, Object> guest = one(id);
        guest.put("bookings_count", jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE guest_id = ?", Long.class, id));
        guest.put("stays_count", jdbc.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE guest_id = ? AND status IN "
                        + "('checked_in','checked_out')", Long.class, id));
        return guest;
    }

    @PostMapping("/api/guests/link")
    public Map<String, Object> link(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:update");
        Number guestId = num(body, "guest_id");
        if (guestId == null) {
            throw ApiError.badRequest("Guest ID is required");
        }
        jdbc.update("""
                UPDATE users SET guest_id = ? WHERE id = ? AND deleted_at IS NULL
                """, guestId.longValue(), userId);
        audit.event(userId, "guest_account_linked", "guest", guestId.longValue(), null);
        return message("Account linked successfully");
    }

    @DeleteMapping("/api/guests/unlink/{guestId}")
    public Map<String, Object> unlink(@PathVariable long guestId) {
        long userId = CurrentUser.require().userId();
        jdbc.update("UPDATE users SET guest_id = NULL WHERE id = ? AND guest_id = ?",
                userId, guestId);
        audit.event(userId, "guest_account_unlinked", "guest", guestId, null);
        return message("Account unlinked successfully");
    }

    @GetMapping("/api/guests/my-guests")
    public List<Map<String, Object>> myGuests() {
        long userId = CurrentUser.require().userId();
        return jdbc.queryForList("SELECT * FROM guests WHERE created_by = ? AND deleted_at IS NULL"
                + " ORDER BY created_at DESC", userId);
    }

    @PostMapping("/api/guests/upgrade")
    public Map<String, Object> upgrade(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:update");
        Number bookingId = num(body, "booking_id");
        if (bookingId == null) {
            throw ApiError.badRequest("Booking ID is required");
        }
        jdbc.update("UPDATE bookings SET room_id = ? WHERE id = ?",
                num(body, "new_room_id"), bookingId.longValue());
        audit.event(userId, "guest_upgraded", "booking", bookingId.longValue(),
                Map.of("new_room_id", body.get("new_room_id")));
        return message("Upgrade applied successfully");
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM guests WHERE id = ? AND deleted_at IS NULL", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Guest not found");
        }
        return rows.get(0);
    }

    static String like(String value) {
        return "%" + value + "%";
    }

    static String joinName(String first, String last) {
        return (first == null ? "" : first) + (last == null ? "" : " " + last).trim();
    }

    public static Object[] concat(Object[] base, Object... extra) {
        Object[] all = new Object[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
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
}
