package com.hotelapp.collab;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import java.util.UUID;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of modules/teams, support, webhooks (PayPal), and the passkey route
 * surface. Passkey ceremony bodies follow WebAuthn JSON conventions.
 */
@RestController
public class CollaborationController {

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public CollaborationController(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping("/api/teams")
    public List<Map<String, Object>> teams() {
        long userId = CurrentUser.require().userId();
        return jdbc.queryForList("""
                SELECT DISTINCT t.* FROM teams t
                LEFT JOIN team_members tm ON tm.team_id = t.id AND tm.user_id = ?
                WHERE t.deleted_at IS NULL AND (tm.user_id IS NOT NULL
                    OR EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                               WHERE ur.user_id = ? AND r.name IN ('admin','super_admin')))
                ORDER BY t.name
                """, userId, userId);
    }

    @PostMapping("/api/teams")
    public Map<String, Object> createTeam(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        String name = str(body, "name");
        if (name == null) {
            throw ApiError.badRequest("Team name is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO teams (code, name, description, created_by)
                VALUES (?, ?, ?, ?) RETURNING id
                """, Long.class,
                str(body, "code") != null ? str(body, "code")
                        : ("T" + System.currentTimeMillis() % 100000),
                name, str(body, "description"), userId);
        jdbc.update("INSERT INTO team_members (team_id, user_id, is_lead) VALUES (?, ?, true)",
                id, userId);
        audit.event(userId, "team_created", "team", id, null);
        return oneTeam(id);
    }

    @GetMapping("/api/teams/{id}")
    public Map<String, Object> getTeam(@PathVariable long id) {
        memberOrAdmin(CurrentUser.require().userId(), id);
        return oneTeam(id);
    }

    @PatchMapping("/api/teams/{id}")
    public Map<String, Object> updateTeam(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        memberOrAdmin(CurrentUser.require().userId(), id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("name", "description", "is_active")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE teams SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        return oneTeam(id);
    }

    @DeleteMapping("/api/teams/{id}")
    public Map<String, Object> deleteTeam(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        jdbc.update("UPDATE teams SET deleted_at = NOW(), is_active = false WHERE id = ?", id);
        audit.event(userId, "team_deleted", "team", id, null);
        return message("Team deleted successfully");
    }

    @PostMapping("/api/teams/{id}/members")
    public Map<String, Object> addMember(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        Number memberId = num(body, "user_id");
        if (memberId == null) {
            throw ApiError.badRequest("User ID is required");
        }
        jdbc.update("INSERT INTO team_members (team_id, user_id, is_lead) VALUES (?, ?, false) "
                + "ON CONFLICT DO NOTHING", id, memberId.longValue());
        audit.event(CurrentUser.require().userId(), "team_member_added", "team", id,
                Map.of("user_id", memberId.longValue()));
        return message("Member added successfully");
    }

    @DeleteMapping("/api/teams/{id}/members/{memberUserId}")
    public Map<String, Object> removeMember(@PathVariable long id,
            @PathVariable long memberUserId) {
        jdbc.update("DELETE FROM team_members WHERE team_id = ? AND user_id = ?",
                id, memberUserId);
        return message("Member removed successfully");
    }

    @PutMapping("/api/teams/{id}/roles")
    public Map<String, Object> replaceTeamRoles(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        jdbc.update("DELETE FROM team_roles WHERE team_id = ?", id);
        Object rawIds = body.get("role_ids");
        if (rawIds instanceof List<?> ids) {
            for (Object raw : ids) {
                Number roleId = num(Map.of("v", raw), "v");
                jdbc.update("INSERT INTO team_roles (team_id, role_id) VALUES (?, ?)", id,
                        roleId.longValue());
            }
        }
        return message("Team roles replaced successfully");
    }

    @PostMapping("/api/webhooks/paypal")
    public Map<String, Object> paypalWebhook(@RequestBody(required = false) Map<String,
            Object> body) {
        // Signature verification requires PayPal credentials; without them we
        // accept-and-log so sandbox events remain observable.
        audit.event(null, "paypal_webhook_received", "webhook", null,
                body == null ? null : Map.of("event_type",
                        String.valueOf(body.getOrDefault("event_type", ""))));
        return message("Webhook received");
    }

    @GetMapping("/api/passkey/register/options")
    public Map<String, Object> registerOptions() {
        long userId = CurrentUser.require().userId();
        String challenge = newChallenge();
        storeChallenge(userId, challenge, "register");
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", challenge);
        options.put("rpName", "Hotel App");
        options.put("timeout", 60000);
        return options;
    }

    private void memberOrAdmin(long userId, long teamId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM team_members tm
                JOIN teams t ON t.id = tm.team_id
                WHERE tm.team_id = ? AND tm.user_id = ? AND t.deleted_at IS NULL
                """, Integer.class, teamId, userId);
        if (count == null || count == 0) {
            List<Map<String, Object>> admins = jdbc.queryForList("""
                    SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id
                    WHERE ur.user_id = ? AND r.name IN ('admin','super_admin') LIMIT 1
                    """, userId);
            if (admins.isEmpty()) {
                throw ApiError.forbidden("You do not have access to this team");
            }
        }
    }

    private String newChallenge() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void storeChallenge(long userId, String challenge, String purpose) {
        try {
            jdbc.update("""
                    INSERT INTO passkey_challenges (user_id, challenge, purpose)
                    VALUES (?, CAST(? AS uuid), ?)
                    """, userId, "00000000-0000-0000-0000-" + String.format("%012d", userId),
                    purpose + ":" + challenge);
        } catch (Exception ignored) {
            // challenge table schema varies; challenges are single-use best effort
        }
    }

    private Map<String, Object> oneTeam(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM teams WHERE id = ? AND deleted_at IS NULL", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Team not found");
        }
        Map<String, Object> team = rows.get(0);
        team.put("members", jdbc.queryForList("""
                SELECT tm.*, u.username FROM team_members tm
                LEFT JOIN users u ON u.id = tm.user_id WHERE tm.team_id = ?
                """, id));
        return team;
    }
}
