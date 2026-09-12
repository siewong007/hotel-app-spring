package com.hotelapp.engagement;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.web.Page;
import java.math.BigDecimal;
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
 * Port of modules/loyalty, promotions and communications routes.
 */
@RestController
public class EngagementController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public EngagementController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/admin/loyalty/rules")
    public Map<String, Object> rules() {
        gateLoyaltyManage();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM loyalty_program_rules ORDER BY id LIMIT 1");
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    @PutMapping("/api/admin/loyalty/rules")
    public Map<String, Object> updateRules(@RequestBody Map<String, Object> body) {
        gateLoyaltyManage();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM loyalty_program_rules", Long.class) == 0) {
            jdbc.update("INSERT INTO loyalty_program_rules DEFAULT VALUES");
        }
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("points_per_dollar", "min_redemption_points",
                "points_expiry_months")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE loyalty_program_rules SET " + String.join(", ", sets.keySet())
                    + " WHERE id = (SELECT MIN(id) FROM loyalty_program_rules)",
                    sets.values().toArray());
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM loyalty_program_rules ORDER BY id LIMIT 1");
        return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
    }

    @GetMapping("/api/admin/loyalty/members")
    public Map<String, Object> members(@RequestParam Map<String, String> q) {
        long page = Page.page(q);
        long size = Page.pageSize(q);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM loyalty_members", Long.class);
        List<Map<String, Object>> data = jdbc.queryForList("""
                SELECT lm.*, g.nick_name AS guest_name, g.email AS guest_email
                FROM loyalty_members lm
                LEFT JOIN guests g ON g.id = lm.guest_id
                ORDER BY lm.created_at DESC LIMIT ? OFFSET ?
                """, size, Page.offset(page, size));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @GetMapping("/api/admin/loyalty/members/{id}")
    public Map<String, Object> memberDetail(@PathVariable long id) {
        gateLoyaltyRead();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT lm.*, g.nick_name AS guest_name, g.email AS guest_email
                FROM loyalty_members lm
                LEFT JOIN guests g ON g.id = lm.guest_id WHERE lm.id = ?
                """, id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Member not found");
        }
        return rows.get(0);
    }

    @PostMapping("/api/admin/loyalty/members/{id}/gifts")
    public Map<String, Object> giftPoints(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gateLoyaltyManage();
        BigDecimal points = dec(body.get("points"));
        if (points == null || points.signum() <= 0) {
            throw ApiError.badRequest("Positive points amount is required");
        }
        jdbc.update("UPDATE loyalty_members SET points_balance = "
                + "COALESCE(points_balance,0) + ? WHERE id = ?", points, id);
        jdbc.update("""
                INSERT INTO loyalty_transactions (member_id, transaction_type, points, description)
                VALUES (?, 'earn', ?, 'Admin gift')
                """, id, points);
        audit.event(userId, "loyalty_points_gifted", "loyalty_member", id,
                Map.of("points", points));
        return message("Points gifted successfully");
    }

    @GetMapping("/api/admin/loyalty/rewards")
    public List<Map<String, Object>> adminRewards() {
        gateLoyaltyRead();
        return jdbc.queryForList("SELECT * FROM loyalty_rewards ORDER BY valid_from DESC");
    }

    @PostMapping("/api/admin/loyalty/rewards")
    public Map<String, Object> createReward(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gateLoyaltyManage();
        String name = str(body, "name");
        if (name == null) {
            throw ApiError.badRequest("Reward name is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO loyalty_rewards (name, description, category, points_cost,
                    requires_approval, is_active)
                VALUES (?, ?, COALESCE(?,'discount'), ?, COALESCE(?, false), true)
                RETURNING id
                """, Long.class, name, str(body, "description"), str(body, "category"),
                num(body, "points_cost"), body.get("requires_approval"));
        audit.event(userId, "loyalty_reward_created", "loyalty_reward", id, null);
        return one("loyalty_rewards", "id", id);
    }

    @PutMapping("/api/admin/loyalty/rewards/{id}")
    public Map<String, Object> updateReward(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        gateLoyaltyManage();
        one("loyalty_rewards", "id", id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("name", "description", "category", "points_cost",
                "requires_approval", "is_active")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE loyalty_rewards SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(CurrentUser.require().userId(), "loyalty_reward_updated",
                "loyalty_reward", id, null);
        return one("loyalty_rewards", "id", id);
    }

    @GetMapping("/api/admin/loyalty/redemptions")
    public List<Map<String, Object>> redemptions() {
        gateLoyaltyRead();
        return jdbc.queryForList(
                "SELECT * FROM loyalty_redemptions ORDER BY created_at DESC LIMIT 200");
    }

    @PutMapping("/api/admin/loyalty/redemptions/{id}/approve")
    public Map<String, Object> approveRedemption(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gateLoyaltyManage();
        int updated = jdbc.update(
                "UPDATE loyalty_redemptions SET status = 'approved' WHERE id = ?", id);
        if (updated == 0) {
            throw ApiError.notFound("Redemption not found");
        }
        audit.event(userId, "loyalty_redemption_approved", "loyalty_redemption", id, null);
        return message("Redemption approved successfully");
    }

    @PutMapping("/api/admin/loyalty/redemptions/{id}/reject")
    public Map<String, Object> rejectRedemption(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gateLoyaltyManage();
        int updated = jdbc.update(
                "UPDATE loyalty_redemptions SET status = 'rejected' WHERE id = ?", id);
        if (updated == 0) {
            throw ApiError.notFound("Redemption not found");
        }
        audit.event(userId, "loyalty_redemption_rejected", "loyalty_redemption", id, null);
        return message("Redemption rejected successfully");
    }

    // Promotions: the legacy /api/promotions CRUD stubs were replaced by the
    // upstream modules/promotions surface — public catalogue plus
    // /api/admin/promotions* and /api/admin/vouchers* in PromotionsController.

    private void gate(long userId, String permission) {
        com.hotelapp.core.security.PermissionGateHelper.check(userId, permission);
    }

    private void gateAnyOf(long userId, List<String> permissions) {
        com.hotelapp.core.security.PermissionGateHelper.checkAny(userId, permissions);
    }

    private void gateLoyaltyRead() {
        gateAnyOf(CurrentUser.require().userId(),
                List.of("loyalty:read", "loyalty:manage"));
    }

    private void gateLoyaltyManage() {
        gateAnyOf(CurrentUser.require().userId(), List.of("loyalty:manage"));
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
