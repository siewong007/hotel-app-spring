package com.hotelapp.insights;

import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.RbacService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of routes/analytics.rs + reports + search: occupancy/booking analytics,
 * benchmark/personalized endpoints, generated reports and global search.
 */
@RestController
public class InsightsController {

    private final JdbcTemplate jdbc;
    private final RbacService rbac;

    public InsightsController(JdbcTemplate jdbc, RbacService rbac) {
        this.jdbc = jdbc;
        this.rbac = rbac;
    }

    @GetMapping("/api/analytics/occupancy")
    public Map<String, Object> occupancy(@RequestParam Map<String, String> q) {
        CurrentUser.require();
        String from = q.getOrDefault("start_date",
                LocalDate().minusDays(30).toString());
        String to = q.getOrDefault("end_date", LocalDate().toString());
        Map<String, Object> totals = jdbc.queryForMap("""
                SELECT (SELECT COUNT(*) FROM rooms) AS total_rooms,
                       COUNT(DISTINCT b.id) FILTER (
                           WHERE b.status IN ('reserved','checked_in','checked_out'))
                           AS booked_rooms,
                       COUNT(DISTINCT b.id) FILTER (WHERE b.status = 'checked_in')
                           AS occupied_rooms,
                       COALESCE(SUM(b.total_amount), 0) AS revenue
                FROM bookings b
                WHERE b.check_out_date >= CAST(? AS date)
                  AND b.check_in_date <= CAST(? AS date)
                """, from, to);
        long totalRooms = ((Number) totals.get("total_rooms")).longValue();
        BigDecimal occupied = new BigDecimal(totals.get("occupied_rooms").toString());
        BigDecimal rate = totalRooms == 0 ? BigDecimal.ZERO
                : occupied.multiply(new BigDecimal("100"))
                        .divide(BigDecimal.valueOf(totalRooms), 2, java.math.RoundingMode.HALF_UP);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("start_date", from);
        body.put("end_date", to);
        body.put("total_rooms", totalRooms);
        body.put("booked_rooms", totals.get("booked_rooms"));
        body.put("occupied_rooms", totals.get("occupied_rooms"));
        body.put("occupancy_rate", rate);
        body.put("revenue", totals.get("revenue"));
        return body;
    }

    @GetMapping("/api/analytics/bookings")
    public Map<String, Object> bookingAnalytics(@RequestParam Map<String, String> q) {
        CurrentUser.require();
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS total_bookings,
                       COUNT(*) FILTER (WHERE status = 'reserved') AS reserved,
                       COUNT(*) FILTER (WHERE status = 'checked_in') AS checked_in,
                       COUNT(*) FILTER (WHERE status = 'checked_out') AS checked_out,
                       COUNT(*) FILTER (WHERE status = 'void') AS cancelled,
                       COALESCE(AVG(nights), 0) AS avg_nights,
                       COALESCE(SUM(total_amount), 0) AS total_revenue
                FROM bookings
                WHERE check_in_date >= CURRENT_DATE - INTERVAL '90 days'
                """);
    }

    @GetMapping("/api/analytics/benchmark")
    public Map<String, Object> benchmark() {
        CurrentUser.require();
        Map<String, Object> ours = jdbc.queryForMap("""
                SELECT COALESCE(AVG(total_amount / GREATEST(nights,1)), 0) AS adr,
                       COUNT(*) FILTER (WHERE is_complimentary) AS complimentary_count
                FROM bookings WHERE status IN ('checked_in','checked_out')
                """);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("adr", ours.get("adr"));
        body.put("industry_adr_percentile", 50);
        body.put("complimentary_share", ours.get("complimentary_count"));
        body.put("benchmark_source", "internal");
        return body;
    }

    @GetMapping("/api/analytics/personalized")
    public Map<String, Object> personalized() {
        long userId = CurrentUser.require().userId();
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS my_bookings,
                       COUNT(*) FILTER (WHERE status = 'checked_in') AS active_stays
                FROM bookings WHERE created_by = ?
                """, userId);
    }

    @GetMapping("/api/reports/generate")
    public Map<String, Object> generateReport(@RequestParam Map<String, String> q) {
        CurrentUser.require();
        String type = q.get("type");
        if (type == null || type.isBlank()) {
            throw ApiError.badRequest("Report type is required");
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("type", type);
        report.put("generated_at", java.time.Instant.now().toString());
        switch (type) {
            case "occupancy" -> report.put("data", occupancy(q));
            case "bookings" -> report.put("data", bookingAnalytics(q));
            case "revenue" -> report.put("data", jdbc.queryForList("""
                    SELECT transaction_date::text AS day, SUM(amount) AS amount
                    FROM customer_ledgers WHERE status <> 'void'
                    GROUP BY transaction_date ORDER BY day DESC LIMIT 60
                    """));
            default -> throw ApiError.badRequest(
                    "Unknown report type. Supported: occupancy, bookings, revenue");
        }
        return report;
    }

    @GetMapping("/api/search")
    public Map<String, Object> globalSearch(@RequestParam Map<String, String> params) {
        long userId = CurrentUser.require().userId();
        if (rbac.hasRole(userId, "guest")) {
            throw ApiError.forbidden(
                    "Global search is not available to guest accounts");
        }
        String query = params.getOrDefault("q", "").trim();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        List<Map<String, Object>> groups = new java.util.ArrayList<>();
        response.put("groups", groups);
        if (query.length() < 2) {
            return response;
        }
        long limit = com.hotelapp.core.web.Page.parse(params.get("limit"), 6, 1);
        if (limit > 15) {
            limit = 15;
        }
        List<String> wantedTypes = new java.util.ArrayList<>();
        String types = params.get("types");
        if (types != null && !types.trim().isEmpty()) {
            for (String item : types.split(",")) {
                wantedTypes.add(item.trim().toLowerCase());
            }
        }
        String pattern = "%" + query + "%";

        if (wantsType(wantedTypes, "bookings") && rbac.hasPermission(userId, "bookings:read")) {
            List<Map<String, Object>> hits = searchBookings(pattern, limit);
            if (!hits.isEmpty()) {
                groups.add(group("bookings", "Bookings", hits));
            }
        }
        if (wantsType(wantedTypes, "guests") && rbac.hasPermission(userId, "guests:read")) {
            List<Map<String, Object>> hits = searchGuests(pattern, limit);
            if (!hits.isEmpty()) {
                groups.add(group("guests", "Guests", hits));
            }
        }
        if (wantsType(wantedTypes, "ledgers") && rbac.hasPermission(userId, "ledgers:read")) {
            List<Map<String, Object>> hits = searchLedgers(pattern, query, limit);
            if (!hits.isEmpty()) {
                groups.add(group("ledgers", "Ledger", hits));
            }
        }
        if (wantsType(wantedTypes, "rooms") && rbac.hasPermission(userId, "rooms:read")) {
            List<Map<String, Object>> hits = searchRooms(pattern, limit);
            if (!hits.isEmpty()) {
                groups.add(group("rooms", "Rooms", hits));
            }
        }
        return response;
    }

    private List<Map<String, Object>> searchBookings(String pattern, long limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT b.id AS id, b.booking_number AS booking_number,
                       COALESCE(g.nick_name, '') AS guest_name,
                       COALESCE(r.room_number, '') AS room_number,
                       b.status AS status
                FROM bookings b
                LEFT JOIN guests g ON b.guest_id = g.id
                LEFT JOIN rooms r ON b.room_id = r.id
                WHERE b.status != 'voided' AND (
                    b.booking_number ILIKE ? OR g.nick_name ILIKE ? OR r.room_number ILIKE ?)
                ORDER BY b.check_in_date DESC LIMIT ?
                """, pattern, pattern, pattern, limit);
        List<Map<String, Object>> hits = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String bookingNumber = (String) row.get("booking_number");
            String label;
            String routeSearch;
            if (bookingNumber == null || bookingNumber.trim().isEmpty()) {
                label = "#" + row.get("id");
                routeSearch = String.valueOf(row.get("id"));
            } else {
                label = bookingNumber;
                routeSearch = bookingNumber;
            }
            StringBuilder subtitle = new StringBuilder();
            String guestName = (String) row.get("guest_name");
            if (!guestName.isEmpty()) {
                subtitle.append(guestName);
            }
            String roomNumber = (String) row.get("room_number");
            if (!roomNumber.isEmpty()) {
                if (subtitle.length() > 0) {
                    subtitle.append(" · ");
                }
                subtitle.append("Room ").append(roomNumber);
            }
            if (subtitle.length() > 0) {
                subtitle.append(" · ");
            }
            subtitle.append(((String) row.get("status")).replace('_', ' '));
            hits.add(hit(row.get("id"), label, subtitle.toString(),
                    "/bookings?search=" + encodeQuery(routeSearch) + "&booking_id="
                            + row.get("id")));
        }
        return hits;
    }

    private List<Map<String, Object>> searchGuests(String pattern, long limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT g.id AS id,
                       COALESCE(g.nick_name,
                           TRIM(COALESCE(g.first_name, '') || ' ' || COALESCE(g.last_name, '')),
                           '') AS full_name,
                       COALESCE(g.phone, '') AS phone, COALESCE(g.email, '') AS email,
                       COALESCE(g.ic_number, '') AS ic_number,
                       COALESCE(g.company_name, '') AS company_name
                FROM guests g
                WHERE g.deleted_at IS NULL AND (
                    CAST(g.id AS TEXT) ILIKE ?
                    OR COALESCE(g.nick_name, '') ILIKE ?
                    OR COALESCE(g.first_name, '') ILIKE ?
                    OR COALESCE(g.last_name, '') ILIKE ?
                    OR TRIM(COALESCE(g.first_name, '') || ' ' || COALESCE(g.last_name, '')) ILIKE ?
                    OR COALESCE(g.email, '') ILIKE ?
                    OR COALESCE(g.phone, '') ILIKE ?
                    OR COALESCE(g.ic_number, '') ILIKE ?
                    OR COALESCE(g.company_name, '') ILIKE ?
                    OR EXISTS (SELECT 1 FROM users u
                               WHERE u.guest_id = g.id
                                 AND u.deleted_at IS NULL
                                 AND u.is_active = true
                                 AND u.username ILIKE ?))
                ORDER BY full_name LIMIT ?
                """, pattern, pattern, pattern, pattern, pattern, pattern, pattern, pattern,
                pattern, pattern, limit);
        List<Map<String, Object>> hits = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String fullName = (String) row.get("full_name");
            List<String> parts = new java.util.ArrayList<>();
            parts.add("#" + row.get("id"));
            for (String key : List.of("phone", "email", "ic_number", "company_name")) {
                String value = (String) row.get(key);
                if (!value.isEmpty()) {
                    parts.add(value);
                }
            }
            hits.add(hit(row.get("id"), fullName, String.join(" · ", parts),
                    "/guest-config?search=" + encodeQuery(fullName) + "&guest_id="
                            + row.get("id")));
        }
        return hits;
    }

    private List<Map<String, Object>> searchLedgers(String pattern, String query,
            long limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT cl.id AS id, cl.company_name AS company_name,
                       c.id AS company_id,
                       COALESCE(cl.description, '') AS description,
                       COALESCE(cl.invoice_number, '') AS invoice_number,
                       COALESCE(cl.folio_number, '') AS folio_number,
                       COALESCE(b.booking_number, '') AS booking_number,
                       COALESCE(cl.room_number, '') AS room_number,
                       cl.status AS status
                FROM customer_ledgers cl
                LEFT JOIN companies c ON LOWER(c.company_name) = LOWER(cl.company_name)
                LEFT JOIN bookings b ON b.id = cl.booking_id
                WHERE CAST(cl.id AS TEXT) ILIKE ?
                   OR cl.company_name ILIKE ?
                   OR COALESCE(cl.description, '') ILIKE ?
                   OR COALESCE(cl.invoice_number, '') ILIKE ?
                   OR COALESCE(cl.folio_number, '') ILIKE ?
                   OR COALESCE(b.booking_number, '') ILIKE ?
                   OR COALESCE(cl.reference_number, '') ILIKE ?
                   OR COALESCE(cl.payment_reference, '') ILIKE ?
                   OR COALESCE(cl.room_number, '') ILIKE ?
                ORDER BY cl.created_at DESC LIMIT ?
                """, pattern, pattern, pattern, pattern, pattern, pattern, pattern,
                pattern, pattern, limit);
        List<Map<String, Object>> hits = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String lowered = query.toLowerCase();
            String invoice = (String) row.get("invoice_number");
            String folio = (String) row.get("folio_number");
            String booking = (String) row.get("booking_number");
            String title = null;
            String routeSearch = null;
            for (String value : List.of(invoice, folio, booking)) {
                if (!lowered.isEmpty() && !value.isEmpty()
                        && value.toLowerCase().contains(lowered)) {
                    title = value;
                    routeSearch = value;
                    break;
                }
            }
            if (title == null) {
                for (String value : List.of(invoice, folio, booking)) {
                    if (!value.isEmpty()) {
                        title = value;
                        routeSearch = value;
                        break;
                    }
                }
            }
            if (title == null) {
                title = "Ledger #" + row.get("id");
                routeSearch = String.valueOf(row.get("id"));
            }
            StringBuilder subtitle = new StringBuilder((String) row.get("company_name"));
            if (!booking.isEmpty() && !booking.equals(title)) {
                subtitle.append(" · ").append(booking);
            }
            String description = (String) row.get("description");
            if (!description.isEmpty()) {
                subtitle.append(" · ").append(description);
            }
            String roomNumber = (String) row.get("room_number");
            if (!roomNumber.isEmpty()) {
                subtitle.append(" · Room ").append(roomNumber);
            }
            String status = (String) row.get("status");
            if (!status.isEmpty()) {
                subtitle.append(" · ").append(status.replace('_', ' '));
            }
            Object companyId = row.get("company_id");
            String companyContext = companyId != null
                    ? "&company_id=" + companyId
                    : "&company=" + encodeQuery((String) row.get("company_name"));
            hits.add(hit(row.get("id"), title, subtitle.toString(),
                    "/company-ledger?tab=entries&search=" + encodeQuery(routeSearch)
                            + "&ledger_id=" + row.get("id") + companyContext));
        }
        return hits;
    }

    private List<Map<String, Object>> searchRooms(String pattern, long limit) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.id AS id, r.room_number AS room_number,
                       COALESCE(rt.name, '') AS room_type,
                       COALESCE(r.status, '') AS status
                FROM rooms r
                LEFT JOIN room_types rt ON r.room_type_id = rt.id
                WHERE r.room_number ILIKE ? OR rt.name ILIKE ? OR rt.code ILIKE ?
                ORDER BY r.room_number LIMIT ?
                """, pattern, pattern, pattern, limit);
        List<Map<String, Object>> hits = new java.util.ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            StringBuilder subtitle = new StringBuilder((String) row.get("room_type"));
            String status = (String) row.get("status");
            if (!status.isEmpty()) {
                subtitle.append(" · ").append(status.replace('_', ' '));
            }
            hits.add(hit(row.get("id"), "Room " + row.get("room_number"),
                    subtitle.toString(), "/room-management"));
        }
        return hits;
    }

    private static boolean wantsType(List<String> wantedTypes, String value) {
        return wantedTypes.isEmpty() || wantedTypes.contains(value);
    }

    private static Map<String, Object> group(String type, String label,
            List<Map<String, Object>> results) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("type", type);
        group.put("label", label);
        group.put("results", results);
        return group;
    }

    private static Map<String, Object> hit(Object id, String title, String subtitle,
            String route) {
        Map<String, Object> hit = new LinkedHashMap<>();
        hit.put("id", id);
        hit.put("title", title);
        hit.put("subtitle", subtitle);
        hit.put("route", route);
        return hit;
    }

    /** RFC 3986 unreserved characters pass through; everything else is %-escaped. */
    private static String encodeQuery(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean unreserved = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.'
                    || c == '~';
            if (unreserved) {
                out.append((char) c);
            } else {
                out.append('%');
                if (c < 16) {
                    out.append('0');
                }
                out.append(Integer.toHexString(c).toUpperCase());
            }
        }
        return out.toString();
    }

    private static java.time.LocalDate LocalDate() {
        return java.time.LocalDate.now();
    }
}
