package com.hotelapp.core.security;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RbacService {

    private static final String EFFECTIVE_ROLES_CTE = """
            WITH effective_roles AS (
                SELECT ur.role_id
                FROM user_roles ur
                WHERE ur.user_id = ?
                  AND (ur.expires_at IS NULL OR ur.expires_at > CURRENT_TIMESTAMP)
                UNION
                SELECT tr.role_id
                FROM team_roles tr
                INNER JOIN team_members tm ON tm.team_id = tr.team_id
                INNER JOIN teams t ON t.id = tm.team_id
                WHERE tm.user_id = ?
                  AND t.is_active
                  AND t.deleted_at IS NULL
                  AND (tm.expires_at IS NULL OR tm.expires_at > CURRENT_TIMESTAMP)
            ) """;

    private static final String EFFECTIVE_PERMISSIONS_SQL =
            EFFECTIVE_ROLES_CTE + """
            SELECT DISTINCT p.name
            FROM permissions p
            INNER JOIN role_permissions rp ON p.id = rp.permission_id
            INNER JOIN effective_roles er ON er.role_id = rp.role_id""";

    private static final String EFFECTIVE_ROLE_NAMES_SQL =
            EFFECTIVE_ROLES_CTE + """
            SELECT DISTINCT r.name
            FROM roles r
            INNER JOIN effective_roles er ON er.role_id = r.id""";

    private record RbacSets(Set<String> permissions, Set<String> roles, Instant loadedAt) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final long ttlSeconds;
    private final Map<Long, RbacSets> cache = new LinkedHashMap<>();

    public RbacService(JdbcTemplate jdbcTemplate,
            @Value("${app.rbac-cache-ttl-secs:30}") long ttlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean hasPermission(long userId, String permission) {
        RbacSets sets = resolve(userId);
        if (sets.permissions().contains(permission)) {
            return true;
        }
        int colon = permission.indexOf(':');
        if (colon > 0) {
            return sets.permissions().contains(permission.substring(0, colon) + ":manage");
        }
        return false;
    }

    public boolean hasRole(long userId, String roleName) {
        return resolve(userId).roles().contains(roleName);
    }

    public synchronized void invalidateAll() {
        cache.clear();
    }

    private RbacSets resolve(long userId) {
        synchronized (cache) {
            RbacSets hit = cache.get(userId);
            if (hit != null && Duration.between(hit.loadedAt(), Instant.now()).getSeconds() < ttlSeconds) {
                return hit;
            }
        }
        Set<String> permissions = new HashSet<>(jdbcTemplate.queryForList(
                EFFECTIVE_PERMISSIONS_SQL, String.class, userId, userId));
        Set<String> roles = new HashSet<>(jdbcTemplate.queryForList(
                EFFECTIVE_ROLE_NAMES_SQL, String.class, userId, userId));
        RbacSets sets = new RbacSets(permissions, roles, Instant.now());
        synchronized (cache) {
            cache.put(userId, sets);
        }
        return sets;
    }
}
