package com.hotelapp.admin;

import static com.hotelapp.rates.RatesController.message;
import static com.hotelapp.rates.RatesController.num;
import static com.hotelapp.rates.RatesController.str;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGate;
import com.hotelapp.core.web.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
public class AdminController {

    private final JdbcTemplate jdbc;
    private final PermissionGate gate;
    private final AuditWriter audit;

    public AdminController(JdbcTemplate jdbc, PermissionGate gate, AuditWriter audit) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.audit = audit;
    }

    @GetMapping("/api/users")
    public Map<String, Object> users(@RequestParam Map<String, String> q) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("users:read", "users:manage"));
        long page = Page.page(q);
        long size = Page.pageSize(q);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", Long.class);
        List<Map<String, Object>> data = jdbc.queryForList("""
                SELECT id, uuid, username, email, full_name, phone, is_active, is_verified,
                       is_super_admin, user_type, created_at, updated_at
                FROM users WHERE deleted_at IS NULL ORDER BY id LIMIT ? OFFSET ?
                """, size, Page.offset(page, size));
        return Page.of(data, total == null ? 0 : total, page, size);
    }

    @PostMapping("/api/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("users:create", "users:manage"));
        String username = str(body, "username");
        String email = str(body, "email");
        String password = str(body, "password");
        if (username == null || email == null || password == null) {
            throw ApiError.badRequest("Username, email and password are required");
        }
        if (password.length() < 8) {
            throw ApiError.badRequest("Password must be at least 8 characters");
        }
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO users (username, email, password_hash, full_name, phone,
                        user_type, is_active)
                    VALUES (?, ?, ?, ?, ?, COALESCE(?, 'staff'), true) RETURNING id
                    """, Long.class, username.toLowerCase(), email.toLowerCase(),
                    new BCryptPasswordEncoder(12).encode(password), str(body, "full_name"),
                    str(body, "phone"), str(body, "user_type"));
            audit.event(userId, "user_created", "user", id, Map.of("new_username", username));
            return oneUser(id);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiError.conflict("Username or email already exists");
        }
    }

    @GetMapping("/api/users/{id}")
    public Map<String, Object> getUser(@PathVariable long id) {
        anyOf(CurrentUser.require().userId(), List.of("users:read", "users:manage"));
        return oneUser(id);
    }

    @PatchMapping("/api/users/{id}")
    public Map<String, Object> updateUser(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("users:update", "users:manage"));
        oneUser(id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("full_name", "phone", "is_active", "is_locked",
                "is_verified", "is_super_admin")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE users SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(userId, "user_updated", "user", id, null);
        return oneUser(id);
    }

    @DeleteMapping("/api/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("users:delete", "users:manage"));
        oneUser(id);
        jdbc.update("UPDATE users SET deleted_at = NOW(), is_active = false WHERE id = ?", id);
        audit.event(userId, "user_deleted", "user", id, null);
        return message("User deleted successfully");
    }

    @PostMapping("/api/users/roles")
    public Map<String, Object> assignRole(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("users:update", "users:manage"));
        Number targetId = num(body, "user_id");
        Number roleId = num(body, "role_id");
        if (targetId == null || roleId == null) {
            throw ApiError.badRequest("User ID and role ID are required");
        }
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", targetId.longValue(), roleId.longValue());
        audit.event(userId, "role_assigned", "user", targetId.longValue(), null);
        return message("Role assigned successfully");
    }

    @PutMapping("/api/users/{id}/roles")
    public Map<String, Object> replaceRoles(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("users:update", "users:manage"));
        oneUser(id);
        Object rawIds = body.get("role_ids");
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", id);
        if (rawIds instanceof List<?> ids) {
            for (Object raw : ids) {
                Number roleId = num(Map.of("v", raw), "v");
                jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?) "
                        + "ON CONFLICT DO NOTHING", id, roleId.longValue());
            }
        }
        audit.event(userId, "user_roles_replaced", "user", id, null);
        return message("User roles replaced successfully");
    }

    @DeleteMapping("/api/users/{id}/roles/{roleId}")
    public Map<String, Object> removeRole(@PathVariable long id, @PathVariable long roleId) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("users:update", "users:manage"));
        jdbc.update("DELETE FROM user_roles WHERE user_id = ? AND role_id = ?", id, roleId);
        audit.event(userId, "role_removed", "user", id, null);
        return message("Role removed successfully");
    }

    @GetMapping("/api/rbac/roles")
    public List<Map<String, Object>> roles() {
        anyOf(CurrentUser.require().userId(),
                List.of("roles:read", "roles:manage", "permissions:read",
                        "permissions:manage"));
        return jdbc.queryForList("SELECT * FROM roles ORDER BY priority DESC");
    }

    @PostMapping("/api/rbac/roles")
    public Map<String, Object> createRole(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("roles:create", "roles:manage"));
        String name = str(body, "name");
        if (name == null) {
            throw ApiError.badRequest("Role name is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO roles (name, display_name, description, is_system_role, priority)
                VALUES (?, COALESCE(?, name), ?, false, COALESCE(?, 10)) RETURNING id
                """, Long.class, name.toLowerCase(), str(body, "display_name"),
                str(body, "description"), num(body, "priority"));
        audit.event(userId, "role_created", "role", id, null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM roles WHERE id = ?", id);
        return rows.get(0);
    }

    @PutMapping("/api/rbac/roles/{id}")
    public Map<String, Object> updateRole(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("roles:update", "roles:manage"));
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("display_name", "description", "priority")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE roles SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(userId, "role_updated", "role", id, null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM roles WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Role not found");
        }
        return rows.get(0);
    }

    @DeleteMapping("/api/rbac/roles/{id}")
    public Map<String, Object> deleteRole(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("roles:delete", "roles:manage"));
        jdbc.update("UPDATE roles SET is_system_role = false WHERE id = ?", id);
        int removed = jdbc.update("DELETE FROM roles WHERE id = ? AND is_system_role = false", id);
        if (removed == 0) {
            throw ApiError.conflict("System roles cannot be deleted");
        }
        audit.event(userId, "role_deleted", "role", id, null);
        return message("Role deleted successfully");
    }

    @GetMapping("/api/rbac/permissions")
    public List<Map<String, Object>> permissions() {
        anyOf(CurrentUser.require().userId(),
                List.of("permissions:read", "permissions:manage", "roles:read",
                        "roles:manage"));
        return jdbc.queryForList("SELECT * FROM permissions ORDER BY resource, action");
    }

    @PostMapping("/api/rbac/permissions")
    public Map<String, Object> createPermission(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("permissions:create", "permissions:manage"));
        String name = str(body, "name");
        if (name == null) {
            throw ApiError.badRequest("Permission name is required");
        }
        try {
            Long id = jdbc.queryForObject("""
                    INSERT INTO permissions (name, resource, action, description, is_system_permission)
                    VALUES (?, SPLIT_PART(?, ':', 1), SPLIT_PART(?, ':', 2), ?, false)
                    RETURNING id
                    """, Long.class, name.toLowerCase(), name.toLowerCase(),
                    str(body, "description"));
            audit.event(userId, "permission_created", "permission", id, null);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM permissions WHERE id = ?", id);
            return rows.get(0);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiError.conflict("Permission already exists");
        }
    }

    @PutMapping("/api/rbac/permissions/{id}")
    public Map<String, Object> updatePermission(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("permissions:update", "permissions:manage"));
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("description")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            jdbc.update("UPDATE permissions SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(userId, "permission_updated", "permission", id, null);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM permissions WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Permission not found");
        }
        return rows.get(0);
    }

    @DeleteMapping("/api/rbac/permissions/{id}")
    public Map<String, Object> deletePermission(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("permissions:delete", "permissions:manage"));
        jdbc.update("DELETE FROM role_permissions WHERE permission_id = ?", id);
        int removed = jdbc.update(
                "DELETE FROM permissions WHERE id = ? AND is_system_permission = false", id);
        if (removed == 0) {
            throw ApiError.conflict("System permissions cannot be deleted");
        }
        audit.event(userId, "permission_deleted", "permission", id, null);
        return message("Permission deleted successfully");
    }

    @PostMapping("/api/rbac/roles/permissions")
    public Map<String, Object> assignPermission(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("permissions:manage"));
        Number roleId = num(body, "role_id");
        Number permissionId = num(body, "permission_id");
        if (roleId == null || permissionId == null) {
            throw ApiError.badRequest("Role ID and permission ID are required");
        }
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", roleId.longValue(), permissionId.longValue());
        audit.event(userId, "permission_assigned", "role", roleId.longValue(), null);
        return message("Permission assigned successfully");
    }

    @GetMapping("/api/rbac/roles/{id}/permissions")
    public List<Map<String, Object>> rolePermissions(@PathVariable long id) {
        anyOf(CurrentUser.require().userId(), List.of("roles:read", "roles:manage"));
        return jdbc.queryForList("""
                SELECT p.* FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                WHERE rp.role_id = ? ORDER BY p.name
                """, id);
    }

    @PutMapping("/api/rbac/roles/{id}/permissions")
    public Map<String, Object> replaceRolePermissions(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("permissions:manage"));
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", id);
        Object rawIds = body.get("permission_ids");
        if (rawIds instanceof List<?> ids) {
            for (Object raw : ids) {
                Number pid = num(Map.of("v", raw), "v");
                jdbc.update("INSERT INTO role_permissions (role_id, permission_id) "
                        + "VALUES (?, ?) ON CONFLICT DO NOTHING", id, pid.longValue());
            }
        }
        audit.event(userId, "role_permissions_replaced", "role", id, null);
        return message("Role permissions replaced successfully");
    }

    @DeleteMapping("/api/rbac/roles/{roleId}/permissions/{permissionId}")
    public Map<String, Object> removeRolePermission(@PathVariable long roleId,
            @PathVariable long permissionId) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("permissions:manage"));
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ?",
                roleId, permissionId);
        audit.event(userId, "permission_removed_from_role", "role", roleId, null);
        return message("Permission removed from role successfully");
    }

    @GetMapping("/api/rbac/snapshot")
    public Map<String, Object> snapshot() {
        long userId = CurrentUser.require().userId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roles", roleNames(userId));
        body.put("permissions", permissionNames(userId));
        body.put("route_policies", jdbc.queryForList(
                "SELECT * FROM route_access_policies ORDER BY route_id"));
        return body;
    }

    @GetMapping("/api/rbac/route-policies")
    public List<Map<String, Object>> routePolicies() {
        anyOf(CurrentUser.require().userId(),
                List.of("roles:read", "roles:manage", "permissions:read",
                        "permissions:manage", "users:read", "users:manage"));
        return jdbc.queryForList(
                "SELECT * FROM route_access_policies ORDER BY route_id");
    }

    @PutMapping("/api/rbac/route-policies/{routeId}")
    public Map<String, Object> updateRoutePolicy(@PathVariable String routeId,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        anyOf(userId, List.of("permissions:manage"));
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("path", "nav_label", "nav_group", "required_permissions",
                "required_roles", "excluded_roles", "nav_permissions", "nav_roles",
                "nav_excluded_roles", "is_navigation")) {
            if (body.containsKey(column)) {
                sets.put(column + " = CAST(? AS jsonb)", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE route_access_policies SET " + String.join(", ", sets.keySet())
                    + " WHERE route_id = ?", append(List.of(routeId),
                    sets.values().toArray()));
        }
        audit.event(userId, "route_policy_updated", "route_policy", null,
                Map.of("route_id", routeId));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM route_access_policies WHERE route_id = ?", routeId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Route policy not found");
        }
        return rows.get(0);
    }

    private void anyOf(long userId, List<String> permissions) {
        gate.checkAny(userId, permissions);
    }

    private List<String> roleNames(long userId) {
        return jdbc.queryForList("""
                SELECT DISTINCT r.name FROM roles r
                JOIN user_roles ur ON ur.role_id = r.id
                WHERE ur.user_id = ? AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                """, String.class, userId);
    }

    private List<String> permissionNames(long userId) {
        return jdbc.queryForList("""
                WITH effective_roles AS (
                    SELECT ur.role_id FROM user_roles ur
                    WHERE ur.user_id = ? AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                    UNION
                    SELECT tr.role_id FROM team_roles tr
                    JOIN team_members tm ON tm.team_id = tr.team_id
                    JOIN teams t ON t.id = tm.team_id
                    WHERE tm.user_id = ? AND t.is_active AND t.deleted_at IS NULL
                      AND (tm.expires_at IS NULL OR tm.expires_at > CURRENT_TIMESTAMP))
                SELECT DISTINCT p.name FROM permissions p
                JOIN role_permissions rp ON p.id = rp.permission_id
                JOIN effective_roles er ON er.role_id = rp.role_id
                """, String.class, userId, userId);
    }

    private Map<String, Object> oneUser(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, uuid, username, email, full_name, phone, avatar_url, is_active,
                       is_verified, is_locked, is_super_admin, two_factor_enabled, user_type,
                       last_login_at, created_at, updated_at
                FROM users WHERE id = ? AND deleted_at IS NULL
                """, id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("User not found");
        }
        Map<String, Object> user = rows.get(0);
        user.put("roles", roleNames(id));
        return user;
    }

    private static Object[] append(List<Object> base, Object... extra) {
        base.addAll(java.util.Arrays.asList(extra));
        return base.toArray();
    }
}
