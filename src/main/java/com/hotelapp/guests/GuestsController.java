package com.hotelapp.guests;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGate;
import com.hotelapp.core.security.RbacService;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.core.web.Page;
import com.hotelapp.promotions.WelcomeVouchers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
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

    private static final Pattern EMAIL = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final long DEFAULT_PAGE_SIZE = 100;
    private static final long MAX_PAGE_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final PermissionGate gate;
    private final RbacService rbac;
    private final AuditWriter audit;
    private final TransactionTemplate tx;

    public GuestsController(JdbcTemplate jdbc, PermissionGate gate, RbacService rbac,
            AuditWriter audit, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.rbac = rbac;
        this.audit = audit;
        this.tx = tx;
    }

    @GetMapping("/api/guests")
    public Map<String, Object> list(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        if (!rbac.hasPermission(userId, "guests:read")
                && !rbac.hasPermission(userId, "guests:manage")) {
            return Page.of(List.of(), 0, 1, DEFAULT_PAGE_SIZE);
        }
        long page = Page.page(q);
        long size = Page.parse(q.get("page_size"), DEFAULT_PAGE_SIZE, 1);
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        StringBuilder filter = new StringBuilder();
        String guestType = blank(q.get("guest_type"));
        if ("member".equals(guestType)) {
            filter.append(" AND guest_type = 'member'");
        } else if ("non_member".equals(guestType)) {
            filter.append(" AND (guest_type = 'non_member' OR guest_type IS NULL)");
        }
        String tourismType = blank(q.get("tourism_type"));
        if ("local".equals(tourismType)) {
            filter.append(" AND tourism_type = 'local'");
        } else if ("foreign".equals(tourismType)) {
            filter.append(" AND tourism_type = 'foreign'");
        }
        if (bool(q.get("missing_tourism"))) {
            filter.append(" AND tourism_type IS NULL");
        }
        if (bool(q.get("missing_info"))) {
            filter.append(" AND ((NULLIF(TRIM(COALESCE(email, '')), '') IS NULL"
                    + " AND NULLIF(TRIM(COALESCE(phone, '')), '') IS NULL)"
                    + " OR NULLIF(TRIM(COALESCE(ic_number, '')), '') IS NULL)");
        }

        String search = blank(q.get("search"));
        String where;
        Object[] args;
        if (search != null) {
            where = filter + " AND (CAST(id AS TEXT) ILIKE ?"
                    + " OR COALESCE(nick_name, '') ILIKE ?"
                    + " OR COALESCE(first_name, '') ILIKE ?"
                    + " OR COALESCE(last_name, '') ILIKE ?"
                    + " OR TRIM(COALESCE(first_name, '') || ' ' || COALESCE(last_name, '')) ILIKE ?"
                    + " OR COALESCE(email, '') ILIKE ?"
                    + " OR COALESCE(phone, '') ILIKE ?"
                    + " OR COALESCE(ic_number, '') ILIKE ?"
                    + " OR COALESCE(company_name, '') ILIKE ?"
                    + " OR EXISTS (SELECT 1 FROM users u"
                    + "            WHERE u.guest_id = guests.id"
                    + "              AND u.deleted_at IS NULL"
                    + "              AND u.is_active = true"
                    + "              AND u.username ILIKE ?))";
            String pattern = "%" + search.trim() + "%";
            args = new Object[] {pattern, pattern, pattern, pattern, pattern, pattern,
                    pattern, pattern, pattern, pattern};
        } else {
            where = filter.toString();
            args = new Object[] {};
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guests WHERE deleted_at IS NULL" + where,
                Long.class, args);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + GuestViews.LIST_COLUMNS + " FROM guests"
                        + " WHERE deleted_at IS NULL" + where
                        + " ORDER BY nick_name LIMIT ? OFFSET ?",
                concat(args, size, Page.offset(page, size)));
        List<Map<String, Object>> data = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            data.add(GuestViews.json(row));
        }
        GuestViews.attachEkycSummaries(jdbc, data);
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/guests")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        String firstNameRaw = str(body, "first_name");
        if (firstNameRaw == null || firstNameRaw.trim().isEmpty()) {
            throw ApiError.badRequest("First name cannot be empty");
        }
        String email = normalizeGuestEmail(str(body, "email"));
        String phone = normalizeGuestPhone(str(body, "phone"));
        String icNumber = normalizeGuestText(str(body, "ic_number"));
        String firstName = Sanitizer.sanitizeGuestName(firstNameRaw);
        String lastNameRaw = str(body, "last_name");
        if (lastNameRaw == null) {
            throw ApiError.badRequest("Last name is required");
        }
        String lastName = Sanitizer.sanitizeGuestName(lastNameRaw);
        String nickName = (firstName + " " + lastName).trim();
        String tourismType = str(body, "tourism_type") == null
                ? "local" : str(body, "tourism_type");
        String guestType = str(body, "guest_type") == null
                ? "non_member" : str(body, "guest_type");
        Number discount = num(body, "discount_percentage");

        Long conflict = nickNameConflictId(nickName, null);
        if (conflict != null) {
            throw duplicateCreateError(nickName, conflict);
        }

        Map<String, Object> guest = tx.execute(status -> {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "INSERT INTO guests (nick_name, first_name, last_name, email, phone,"
                                + " ic_number, nationality, address_line_1, city, state,"
                                + " postal_code, country, guest_type, tourism_type,"
                                + " discount_percentage, company_name, created_by)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                                + " RETURNING " + GuestViews.COLUMNS,
                        nickName, firstName, lastName, email, phone, icNumber,
                        sanitizeText(str(body, "nationality")),
                        sanitizeText(str(body, "address_line1")),
                        sanitizeText(str(body, "city")),
                        sanitizeText(str(body, "state_province")),
                        sanitizeText(str(body, "postal_code")),
                        sanitizeText(str(body, "country")),
                        guestType, tourismType,
                        discount == null ? 0 : discount.intValue(),
                        sanitizeText(str(body, "company_name")), userId);
                return GuestViews.json(rows.get(0));
            } catch (DuplicateKeyException e) {
                Long conflictId = nickNameConflictId(nickName, null);
                throw duplicateCreateError(nickName, conflictId);
            }
        });
        ensureLoyaltyMember(guest);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", guest.get("nick_name"));
        details.put("email", guest.get("email"));
        audit.event(userId, "guest_created", "guest",
                ((Number) guest.get("id")).longValue(), details);
        return guest;
    }

    @GetMapping("/api/guests/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "guests:read");
        Map<String, Object> guest = one(id);
        GuestViews.attachEkycSummary(jdbc, guest);
        return guest;
    }

    @GetMapping("/api/guests/{id}/profile")
    public Map<String, Object> profile(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "guests:read");
        Map<String, Object> guest = one(id);
        GuestViews.attachEkycSummary(jdbc, guest);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("guest", guest);
        profile.put("summary", guestSummary(id));
        profile.put("ekyc_summary", guest.get("ekyc_summary"));
        profile.put("reservations", profileBookings(id));
        profile.put("duplicate_candidates", duplicateCandidates(guest));
        return profile;
    }

    @GetMapping("/api/guests/my-guests")
    public List<Map<String, Object>> myGuests() {
        long userId = CurrentUser.require().userId();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT DISTINCT " + GuestViews.LINKED_COLUMNS
                        + " FROM guests g"
                        + " INNER JOIN user_guests ug ON g.id = ug.guest_id"
                        + " WHERE ug.user_id = ? AND g.deleted_at IS NULL"
                        + " ORDER BY g.nick_name",
                userId);
        List<Map<String, Object>> guests = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            guests.add(GuestViews.json(row));
        }
        GuestViews.attachEkycSummaries(jdbc, guests);
        return guests;
    }

    @PostMapping("/api/guests/link")
    public Map<String, Object> link(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        Number guestId = num(body, "guest_id");
        if (guestId == null) {
            throw ApiError.badRequest("Guest ID is required");
        }
        if (!exists(guestId.longValue())) {
            throw ApiError.notFound("Guest not found");
        }
        jdbc.update("""
                INSERT INTO user_guests (user_id, guest_id, relationship_type,
                    can_book_for, can_view_bookings, can_modify, notes, linked_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, guest_id) DO UPDATE SET
                    relationship_type = EXCLUDED.relationship_type,
                    can_book_for = EXCLUDED.can_book_for,
                    can_view_bookings = EXCLUDED.can_view_bookings,
                    can_modify = EXCLUDED.can_modify,
                    notes = EXCLUDED.notes
                """, userId, guestId.longValue(),
                str(body, "relationship_type") == null ? "owner"
                        : str(body, "relationship_type"),
                body.get("can_book_for") == null || Boolean.TRUE.equals(body.get("can_book_for")),
                body.get("can_view_bookings") == null
                        || Boolean.TRUE.equals(body.get("can_view_bookings")),
                body.get("can_modify") == null || Boolean.TRUE.equals(body.get("can_modify")),
                str(body, "notes"), userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Guest linked successfully");
        out.put("guest_id", guestId.longValue());
        return out;
    }

    @DeleteMapping("/api/guests/unlink/{guestId}")
    public Map<String, Object> unlink(@PathVariable long guestId) {
        long userId = CurrentUser.require().userId();
        int removed = jdbc.update(
                "DELETE FROM user_guests WHERE user_id = ? AND guest_id = ?",
                userId, guestId);
        if (removed == 0) {
            throw ApiError.notFound("Guest link not found");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Guest unlinked successfully");
        out.put("guest_id", guestId);
        return out;
    }

    @PostMapping("/api/guests/upgrade")
    public Map<String, Object> upgrade(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        Number guestId = num(body, "guest_id");
        if (guestId == null) {
            throw ApiError.badRequest("Guest ID is required");
        }
        Boolean canModify = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM user_guests"
                        + " WHERE user_id = ? AND guest_id = ? AND can_modify = true)",
                Boolean.class, userId, guestId.longValue());
        if (!Boolean.TRUE.equals(canModify)) {
            throw ApiError.unauthorized(
                    "You don't have permission to upgrade this guest");
        }
        String username = str(body, "username");
        String password = str(body, "password");
        if (username == null || password == null) {
            throw ApiError.badRequest("Username and password are required");
        }
        if (!exists(guestId.longValue())) {
            throw ApiError.notFound("Guest not found or deleted");
        }
        String hash = new BCryptPasswordEncoder(12).encode(password);
        String role = str(body, "role") == null ? "guest" : str(body, "role");
        Long newUserId;
        try {
            newUserId = jdbc.queryForObject("""
                    INSERT INTO users (username, password_hash, user_type, guest_id,
                        is_active, full_name)
                    VALUES (?, ?, 'guest', ?, true,
                        (SELECT nick_name FROM guests WHERE id = ?))
                    RETURNING id
                    """, Long.class, username, hash, guestId.longValue(), guestId.longValue());
        } catch (DuplicateKeyException e) {
            throw ApiError.badRequest("User with this email already exists");
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id)"
                        + " SELECT ?, id FROM roles WHERE name = ? ON CONFLICT DO NOTHING",
                newUserId, role);
        WelcomeVouchers.issue(jdbc, tx, audit, guestId.longValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "Guest upgraded to user successfully");
        out.put("guest_id", guestId.longValue());
        out.put("user_id", newUserId);
        out.put("username", username);
        return out;
    }

    @PatchMapping("/api/guests/{id}")
    public Map<String, Object> update(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:update");
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT first_name, last_name, email, phone, ic_number, nationality,
                       address_line_1 AS address_line1, city, state AS state_province,
                       postal_code, country, title, alt_phone, company_name,
                       guest_type, tourism_type,
                       COALESCE(discount_percentage, 0) AS discount_percentage
                FROM guests
                WHERE id = ? AND deleted_at IS NULL
                """, id);
        if (existing.isEmpty()) {
            throw ApiError.notFound("Guest not found");
        }
        Map<String, Object> cur = existing.get(0);

        // Upstream fields are Option<T>: a key absent or explicitly null both
        // deserialize to None, which keeps the stored value.
        String firstName = body.get("first_name") != null
                ? str(body, "first_name") : (String) cur.get("first_name");
        String lastName = body.get("last_name") != null
                ? str(body, "last_name") : (String) cur.get("last_name");
        String email = body.get("email") != null
                ? normalizeGuestEmail(str(body, "email"))
                : normalizeStoredGuestEmail((String) cur.get("email"));
        String phone = normalizeGuestPhone(body.get("phone") != null
                ? str(body, "phone") : (String) cur.get("phone"));
        String icNumber = normalizeGuestText(body.get("ic_number") != null
                ? str(body, "ic_number") : (String) cur.get("ic_number"));
        String companyName;
        if (body.get("company_name") != null) {
            String company = str(body, "company_name");
            companyName = company == null || company.trim().isEmpty() ? null : company;
        } else {
            companyName = (String) cur.get("company_name");
        }
        String nickName = ((firstName == null ? "" : firstName.trim()) + " "
                + (lastName == null ? "" : lastName.trim())).trim();
        Long conflict = nickNameConflictId(nickName, id);
        if (conflict != null) {
            throw ApiError.badRequest("A guest with the name '" + nickName
                    + "' already exists (Guest ID #" + conflict
                    + "). Guest names must be unique.");
        }

        Map<String, Object> guest = GuestViews.json(jdbc.queryForList(
                "UPDATE guests SET nick_name = ?, first_name = ?, last_name = ?, email = ?,"
                        + " phone = ?, ic_number = ?, nationality = ?, address_line_1 = ?,"
                        + " city = ?, state = ?, postal_code = ?, country = ?, title = ?,"
                        + " alt_phone = ?, guest_type = ?, tourism_type = ?,"
                        + " discount_percentage = ?, company_name = ?,"
                        + " updated_at = CURRENT_TIMESTAMP"
                        + " WHERE id = ?"
                        + " RETURNING " + GuestViews.COLUMNS,
                nickName, firstName, lastName, email, phone, icNumber,
                pick(body, cur, "nationality"), pick(body, cur, "address_line1"),
                pick(body, cur, "city"), pick(body, cur, "state_province"),
                pick(body, cur, "postal_code"), pick(body, cur, "country"),
                pick(body, cur, "title"), pick(body, cur, "alt_phone"),
                body.get("guest_type") != null ? str(body, "guest_type")
                        : cur.get("guest_type"),
                body.get("tourism_type") != null ? str(body, "tourism_type")
                        : cur.get("tourism_type"),
                body.get("discount_percentage") != null
                        ? (num(body, "discount_percentage") == null ? 0
                                : num(body, "discount_percentage").intValue())
                        : cur.get("discount_percentage"),
                companyName, id).get(0));
        ensureLoyaltyMember(guest);
        audit.event(null, "guest_updated", "guest", id,
                Map.of("name", guest.get("nick_name")));
        GuestViews.attachEkycSummary(jdbc, guest);
        return guest;
    }

    @DeleteMapping("/api/guests/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "guests:delete");
        if (!existsAny(id)) {
            throw ApiError.notFound("Guest not found");
        }
        Boolean checkedIn = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM bookings"
                        + " WHERE guest_id = ? AND status = 'checked_in' LIMIT 1)",
                Boolean.class, id);
        if (Boolean.TRUE.equals(checkedIn)) {
            throw ApiError.badRequest(
                    "Cannot delete guest who is currently checked in."
                            + " Please complete the checkout first.");
        }
        jdbc.update("DELETE FROM guests WHERE id = ?", id);
        audit.event(null, "guest_deleted", "guest", id, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Guest deleted successfully");
        return out;
    }

    @GetMapping("/api/guests/{id}/bookings")
    public List<Map<String, Object>> guestBookings(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "guests:read");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id, b.booking_number, b.check_in_date, b.check_out_date,
                       (b.check_out_date - b.check_in_date) AS nights,
                       b.status, b.total_amount, b.created_at,
                       r.room_number, rt.name AS room_type
                FROM bookings b
                JOIN rooms r ON b.room_id = r.id
                LEFT JOIN room_types rt ON r.room_type_id = rt.id
                WHERE b.guest_id = ?
                ORDER BY
                    CASE
                        WHEN b.status IN ('checked_out', 'completed') THEN 0
                        WHEN b.status IN ('voided', 'comp_void') THEN 1
                        ELSE 2
                    END,
                    b.check_in_date ASC,
                    b.check_out_date ASC,
                    b.id ASC
                """, id);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> shaped = new LinkedHashMap<>();
            shaped.put("id", String.valueOf(row.get("id")));
            shaped.put("booking_number", row.get("booking_number"));
            shaped.put("check_in_date", row.get("check_in_date"));
            shaped.put("check_out_date", row.get("check_out_date"));
            shaped.put("nights", row.get("nights"));
            shaped.put("status", row.get("status"));
            shaped.put("total_amount", String.valueOf(row.get("total_amount")));
            shaped.put("created_at", row.get("created_at"));
            shaped.put("room_number", row.get("room_number"));
            shaped.put("room_type", row.get("room_type") == null
                    ? "" : row.get("room_type"));
            out.add(shaped);
        }
        return out;
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + GuestViews.COLUMNS + " FROM guests"
                        + " WHERE id = ? AND deleted_at IS NULL",
                id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Guest not found");
        }
        return GuestViews.json(rows.get(0));
    }

    private boolean exists(long id) {
        return jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM guests WHERE id = ? AND deleted_at IS NULL)",
                Boolean.class, id) == Boolean.TRUE;
    }

    private boolean existsAny(long id) {
        return jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM guests WHERE id = ?)",
                Boolean.class, id) == Boolean.TRUE;
    }

    private Long nickNameConflictId(String nickName, Long excludeId) {
        List<Long> found = excludeId == null
                ? jdbc.queryForList(
                        "SELECT id FROM guests WHERE LOWER(TRIM(nick_name)) = LOWER(TRIM(?))"
                                + " AND deleted_at IS NULL LIMIT 1",
                        Long.class, nickName)
                : jdbc.queryForList(
                        "SELECT id FROM guests WHERE LOWER(TRIM(nick_name)) = LOWER(TRIM(?))"
                                + " AND deleted_at IS NULL AND id != ? LIMIT 1",
                        Long.class, nickName, excludeId);
        return found.isEmpty() ? null : found.get(0);
    }

    private static ApiError duplicateCreateError(String nickName, Long conflictId) {
        String idText = conflictId == null ? "" : " (Guest ID #" + conflictId + ")";
        return ApiError.badRequest("A guest with the name '" + nickName
                + "' already exists" + idText
                + ". Please select the existing guest instead of creating a new one.");
    }

    private Map<String, Object> guestSummary(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                WITH payment_totals AS (
                    SELECT booking_id,
                        COALESCE(SUM(CASE WHEN status = 'completed' THEN amount ELSE 0 END), 0)
                            AS total_paid,
                        COALESCE(SUM(CASE
                            WHEN status = 'refunded' THEN COALESCE(refund_amount, amount)
                            ELSE COALESCE(refund_amount, 0)
                        END), 0) AS total_refunded
                    FROM payments
                    GROUP BY booking_id
                ),
                guest_bookings AS (
                    SELECT b.*,
                        COALESCE(p.total_paid, 0) AS total_paid,
                        COALESCE(p.total_refunded, 0) AS total_refunded
                    FROM bookings b
                    LEFT JOIN payment_totals p ON p.booking_id = b.id
                    WHERE b.guest_id = ? AND b.status NOT IN ('voided', 'comp_void')
                )
                SELECT
                    COALESCE(SUM(CASE WHEN status IN ('checked_out', 'completed')
                        THEN 1 ELSE 0 END), 0)::BIGINT AS completed_stays,
                    COALESCE(SUM(CASE WHEN status IN ('checked_out', 'completed')
                        THEN nights ELSE 0 END), 0)::BIGINT AS total_nights,
                    COALESCE(SUM(CASE WHEN status IN ('checked_out', 'completed')
                        THEN total_amount ELSE 0 END), 0) AS total_room_revenue,
                    MAX(CASE WHEN status IN ('checked_out', 'completed')
                        THEN check_out_date END) AS last_stay_at,
                    MIN(CASE WHEN check_in_date >= CURRENT_DATE
                        THEN check_in_date END) AS next_stay_at,
                    COALESCE(SUM(GREATEST(total_amount - total_paid + total_refunded, 0)), 0)
                        AS outstanding_balance,
                    COUNT(*)::BIGINT AS total_bookings,
                    (SELECT id FROM bookings
                        WHERE guest_id = ? AND status IN ('checked_in', 'auto_checked_in')
                        ORDER BY check_in_date DESC, id DESC
                        LIMIT 1) AS active_booking_id,
                    (SELECT booking_number FROM bookings
                        WHERE guest_id = ? AND status IN ('checked_in', 'auto_checked_in')
                        ORDER BY check_in_date DESC, id DESC
                        LIMIT 1) AS active_booking_number
                FROM guest_bookings
                """, id, id, id);
        return rows.get(0);
    }

    private List<Map<String, Object>> profileBookings(long id) {
        return jdbc.queryForList("""
                WITH payment_totals AS (
                    SELECT booking_id,
                        COALESCE(SUM(CASE WHEN status = 'completed' THEN amount ELSE 0 END), 0)
                            AS total_paid,
                        COALESCE(SUM(CASE
                            WHEN status = 'refunded' THEN COALESCE(refund_amount, amount)
                            ELSE COALESCE(refund_amount, 0)
                        END), 0) AS total_refunded
                    FROM payments
                    GROUP BY booking_id
                )
                SELECT b.id, b.booking_number, b.check_in_date, b.check_out_date,
                       b.nights::BIGINT AS nights, b.status, b.payment_status,
                       b.total_amount, COALESCE(p.total_paid, 0) AS total_paid,
                       GREATEST(b.total_amount - COALESCE(p.total_paid, 0)
                           + COALESCE(p.total_refunded, 0), 0) AS balance_due,
                       b.created_at, r.room_number,
                       COALESCE(rt.name, '') AS room_type,
                       b.special_requests, b.source
                FROM bookings b
                JOIN rooms r ON b.room_id = r.id
                LEFT JOIN room_types rt ON r.room_type_id = rt.id
                LEFT JOIN payment_totals p ON p.booking_id = b.id
                WHERE b.guest_id = ? AND b.status NOT IN ('voided', 'comp_void')
                ORDER BY b.check_in_date DESC, b.created_at DESC
                LIMIT 50
                """, id);
    }

    private List<Map<String, Object>> duplicateCandidates(Map<String, Object> guest) {
        long guestId = ((Number) guest.get("id")).longValue();
        String email = nonEmpty(normalizeEmail((String) guest.get("email")));
        String phoneDigits = nonEmpty(normalizePhone((String) guest.get("phone")));
        String identity = nonEmpty(normalizeIdentity((String) guest.get("ic_number")));
        String nickName = (String) guest.get("nick_name");
        String normalizedName = normalizeName(nickName);
        String namePattern = "%" + duplicateNameSeed(normalizedName) + "%";

        List<Map<String, Object>> pool = jdbc.queryForList(
                "SELECT " + GuestViews.COLUMNS + " FROM guests"
                        + " WHERE id != ? AND deleted_at IS NULL"
                        + " AND ((?::TEXT IS NOT NULL AND LOWER(TRIM(email)) = LOWER(TRIM(?)))"
                        + "   OR (?::TEXT IS NOT NULL AND regexp_replace(COALESCE(phone, ''), '[^0-9]', '', 'g') = ?)"
                        + "   OR (?::TEXT IS NOT NULL AND LOWER(TRIM(ic_number)) = LOWER(TRIM(?)))"
                        + "   OR LOWER(TRIM(nick_name)) = LOWER(TRIM(?))"
                        + "   OR LOWER(nick_name) LIKE LOWER(?))"
                        + " ORDER BY updated_at DESC LIMIT 100",
                guestId, email, email, phoneDigits, phoneDigits, identity, identity,
                nickName, namePattern);

        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> row : pool) {
            Map<String, Object> candidate = scoreCandidate(guest, GuestViews.json(row));
            if (candidate != null) {
                scored.add(candidate);
            }
        }
        scored.sort((left, right) -> {
            int byScore = Integer.compare((int) right.get("score"), (int) left.get("score"));
            if (byScore != 0) {
                return byScore;
            }
            return String.valueOf(((Map<?, ?>) left.get("guest")).get("nick_name"))
                    .compareTo(String.valueOf(
                            ((Map<?, ?>) right.get("guest")).get("nick_name")));
        });
        return scored.size() > 10 ? scored.subList(0, 10) : scored;
    }

    private Map<String, Object> scoreCandidate(Map<String, Object> target,
            Map<String, Object> candidate) {
        int score = 0;
        List<String> matchReasons = new ArrayList<>();
        List<String> blockingReasons = new ArrayList<>();

        if (matchesNonempty(normalizePhone((String) target.get("phone")),
                normalizePhone((String) candidate.get("phone")))) {
            score += 60;
            matchReasons.add("Same normalized phone");
        }
        if (matchesNonempty(normalizeEmail((String) target.get("email")),
                normalizeEmail((String) candidate.get("email")))) {
            score += 60;
            matchReasons.add("Same normalized email");
        }
        String targetIdentity = normalizeIdentity((String) target.get("ic_number"));
        String candidateIdentity = normalizeIdentity((String) candidate.get("ic_number"));
        if (matchesNonempty(targetIdentity, candidateIdentity)) {
            score += 100;
            matchReasons.add("Same identity document");
        } else if (targetIdentity != null && !targetIdentity.isEmpty()
                && candidateIdentity != null && !candidateIdentity.isEmpty()) {
            blockingReasons.add("Conflicting identity document");
        }
        String targetName = normalizeName((String) target.get("nick_name"));
        String candidateName = normalizeName((String) candidate.get("nick_name"));
        if (!targetName.isEmpty() && targetName.equals(candidateName)) {
            score += 25;
            matchReasons.add("Same full name");
        } else if (namesAreSimilar(targetName, candidateName)) {
            score += 10;
            matchReasons.add("Similar name");
        }
        if (score < 25) {
            return null;
        }
        String action;
        if (!blockingReasons.isEmpty()) {
            action = "do_not_merge";
        } else if (score >= 100) {
            action = "high_confidence_review";
        } else if (score >= 60) {
            action = "contact_match_review";
        } else {
            action = "manual_review";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("guest", candidate);
        out.put("score", score);
        out.put("match_reasons", matchReasons);
        out.put("blocking_reasons", blockingReasons);
        out.put("recommended_action", action);
        return out;
    }

    private void ensureLoyaltyMember(Map<String, Object> guest) {
        if (!"member".equals(guest.get("guest_type"))) {
            return;
        }
        long guestId = ((Number) guest.get("id")).longValue();
        Boolean already = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM loyalty_members WHERE guest_id = ?)",
                Boolean.class, guestId);
        if (Boolean.TRUE.equals(already)) {
            return;
        }
        Long tierId = jdbc.queryForObject(
                "SELECT id FROM loyalty_tiers WHERE is_active = true"
                        + " ORDER BY sort_order LIMIT 1",
                Long.class);
        Long memberId = jdbc.queryForObject(
                "INSERT INTO loyalty_members (guest_id, member_number, status)"
                        + " VALUES (?, ?, 'active') RETURNING id",
                Long.class, guestId, String.format("LP%08d", guestId));
        jdbc.update("INSERT INTO loyalty_accounts (member_id, current_tier_id)"
                + " VALUES (?, ?)", memberId, tierId);
    }

    private static String normalizeGuestEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!EMAIL.matcher(trimmed).matches()) {
            throw ApiError.badRequest("Invalid email format");
        }
        return Sanitizer.sanitizeEmail(trimmed);
    }

    private static String normalizeStoredGuestEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        return Sanitizer.sanitizeEmail(email.trim());
    }

    private static String normalizeGuestPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }
        return Sanitizer.sanitizePhone(phone.trim());
    }

    private static String normalizeGuestText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return Sanitizer.sanitizeText(value.trim());
    }

    private static String sanitizeText(String value) {
        return value == null ? null : Sanitizer.sanitizeText(value);
    }

    private static String blank(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static boolean bool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static Object pick(Map<String, Object> body, Map<String, Object> cur,
            String key) {
        return body.get(key) != null ? str(body, key) : cur.get(key);
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String normalizePhone(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String normalizeIdentity(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : value.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String normalizeName(String value) {
        return value == null ? "" : String.join(" ", value.trim().toLowerCase().split("\\s+"))
                .trim();
    }

    private static String duplicateNameSeed(String normalizedName) {
        for (String part : normalizedName.split("\\s+")) {
            if (part.length() >= 3) {
                return part;
            }
        }
        return normalizedName;
    }

    private static boolean matchesNonempty(String left, String right) {
        return left != null && right != null && !left.isEmpty() && left.equals(right);
    }

    private static boolean namesAreSimilar(String left, String right) {
        if (left.isEmpty() || right.isEmpty() || left.equals(right)) {
            return false;
        }
        String[] leftParts = left.split("\\s+");
        String[] rightParts = right.split("\\s+");
        if (leftParts.length == 0 || rightParts.length == 0) {
            return false;
        }
        return leftParts[leftParts.length - 1].equals(rightParts[rightParts.length - 1])
                && !leftParts[0].isEmpty() && !rightParts[0].isEmpty()
                && leftParts[0].charAt(0) == rightParts[0].charAt(0);
    }

    private static String nonEmpty(String value) {
        return value == null || value.isEmpty() ? null : value;
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

    public static Object[] concat(Object[] base, Object... extra) {
        Object[] all = new Object[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

}
