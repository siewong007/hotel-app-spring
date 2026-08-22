package com.hotelapp.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelapp.core.error.ApiError;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "app.jwt-secret=unit-test-secret-key-that-is-long-enough-32ch")
@Testcontainers
class RbacServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RbacService rbacService;

    @Autowired
    PermissionGate permissionGate;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void resetData() {
        jdbc.execute("DELETE FROM user_roles");
        jdbc.execute("DELETE FROM team_members");
        jdbc.execute("DELETE FROM team_roles");
        jdbc.execute("DELETE FROM role_permissions");
        jdbc.execute("DELETE FROM teams");
        jdbc.execute("DELETE FROM users WHERE id <> 1000");
        rbacService.invalidateAll();
    }

    private long insertUser() {
        return jdbc.queryForObject(
                "INSERT INTO users (username, email) VALUES (?, ?) RETURNING id",
                Long.class, ("user" + System.nanoTime()).toLowerCase(),
                ("u" + System.nanoTime() + "@t.local").toLowerCase());
    }

    private long insertRole(String name) {
        return jdbc.queryForObject(
                "INSERT INTO roles(name) VALUES (?) ON CONFLICT (name) DO UPDATE SET "
                        + "name = EXCLUDED.name RETURNING id", Long.class, name);
    }

    private long insertPermission(String name) {
        return jdbc.queryForObject(
                "INSERT INTO permissions(name) VALUES (?) ON CONFLICT (name) DO UPDATE SET "
                        + "name = EXCLUDED.name RETURNING id", Long.class, name);
    }

    private void grant(long roleId, long permissionId) {
        jdbc.update("INSERT INTO role_permissions(role_id, permission_id) VALUES (?,?) "
                + "ON CONFLICT DO NOTHING", roleId, permissionId);
    }

    private long insertTeam(boolean active) {
        return jdbc.queryForObject(
                "INSERT INTO teams(is_active, deleted_at) VALUES (?, NULL) RETURNING id",
                Long.class, active);
    }

    @Test
    void directRoleGrantsPermissionAndManageImpliesActions() {
        long userId = insertUser();
        long roleId = insertRole("front_desk");
        long bookingsRead = insertPermission("bookings:read");
        long roomsManage = insertPermission("rooms:manage");
        grant(roleId, bookingsRead);
        grant(roleId, roomsManage);
        jdbc.update("INSERT INTO user_roles(user_id, role_id, assigned_at) VALUES (?,?,NOW())",
                userId, roleId);
        rbacService.invalidateAll();

        assertThat(rbacService.hasPermission(userId, "bookings:read")).isTrue();
        assertThat(rbacService.hasPermission(userId, "bookings:update")).isFalse();
        assertThat(rbacService.hasPermission(userId, "rooms:create")).isTrue();
        assertThat(rbacService.hasRole(userId, "front_desk")).isTrue();
    }

    @Test
    void expiredUserRoleStopsConferringPermissionsImmediately() {
        long userId = insertUser();
        long roleId = insertRole("expired_role");
        long perm = insertPermission("ledgers:read");
        grant(roleId, perm);
        jdbc.update("INSERT INTO user_roles(user_id, role_id, expires_at) VALUES (?,?,NOW() - INTERVAL '1 minute')",
                userId, roleId);
        rbacService.invalidateAll();
        assertThat(rbacService.hasPermission(userId, "ledgers:read")).isFalse();
    }

    @Test
    void activeTeamMembershipConfersTeamRoles() {
        long userId = insertUser();
        long roleId = insertRole("team_viewer");
        long perm = insertPermission("reports:view");
        grant(roleId, perm);
        long teamId = insertTeam(true);
        jdbc.update("INSERT INTO team_members(team_id, user_id, joined_at) VALUES (?,?,NOW())",
                teamId, userId);
        jdbc.update("INSERT INTO team_roles(team_id, role_id) VALUES (?,?)", teamId, roleId);
        rbacService.invalidateAll();
        assertThat(rbacService.hasPermission(userId, "reports:view")).isTrue();
    }

    @Test
    void inactiveTeamsDoNotConferRoles() {
        long userId = insertUser();
        long roleId = insertRole("dead_team_role");
        long perm = insertPermission("audit:read");
        grant(roleId, perm);
        long teamId = insertTeam(false);
        jdbc.update("INSERT INTO team_members(team_id, user_id, joined_at) VALUES (?,?,NOW())",
                teamId, userId);
        jdbc.update("INSERT INTO team_roles(team_id, role_id) VALUES (?,?)", teamId, roleId);
        rbacService.invalidateAll();
        assertThat(rbacService.hasPermission(userId, "audit:read")).isFalse();
    }

    @Test
    void gateThrowsExactForbiddenEnvelope() {
        long userId = insertUser();
        rbacService.invalidateAll();
        assertThatThrownBy(() -> permissionGate.check(userId, "bookings:create"))
                .isInstanceOfSatisfying(ApiError.class,
                        error -> assertThat(error.message())
                                .isEqualTo("Missing permission: bookings:create"));
    }

    @Test
    void gateAnyOfPassesOnFirstMatchAndDeniesWithJoinedList() {
        long userId = insertUser();
        long roleId = insertRole("any_of_holder");
        long perm = insertPermission("settings:read");
        grant(roleId, perm);
        jdbc.update("INSERT INTO user_roles(user_id, role_id, assigned_at) VALUES (?,?,NOW())",
                userId, roleId);
        rbacService.invalidateAll();

        permissionGate.checkAny(userId, List.of("settings:manage", "settings:read"));

        assertThatThrownBy(() -> permissionGate.checkAny(userId,
                List.of("alpha:x", "beta:y")))
                .isInstanceOfSatisfying(ApiError.class,
                        error -> assertThat(error.message()).isEqualTo(
                                "Missing one of required permissions: alpha:x, beta:y"));
    }
}
