package com.hotelapp.insights;

import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
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

    public InsightsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
    public Map<String, Object> globalSearch(@RequestParam("q") String query) {
        CurrentUser.require();
        if (query == null || query.isBlank()) {
            throw ApiError.badRequest("Search term is required");
        }
        String like = "%" + query + "%";
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("guests", jdbc.queryForList("""
                SELECT id, full_name, email, phone FROM guests
                WHERE deleted_at IS NULL AND (full_name ILIKE ? OR email ILIKE ? OR phone ILIKE ?)
                LIMIT 10
                """, like, like, like));
        results.put("bookings", jdbc.queryForList("""
                SELECT id, booking_number, guest_name, status, check_in_date, check_out_date
                FROM bookings WHERE booking_number ILIKE ? OR guest_name ILIKE ? LIMIT 10
                """, like, like));
        results.put("rooms", jdbc.queryForList(
                "SELECT id, room_number, floor, status FROM rooms WHERE room_number ILIKE ? "
                        + "LIMIT 10", like));
        results.put("companies", jdbc.queryForList(
                "SELECT id, name, contact_person FROM companies WHERE name ILIKE ? LIMIT 10",
                like));
        return results;
    }

    private static java.time.LocalDate LocalDate() {
        return java.time.LocalDate.now();
    }
}
