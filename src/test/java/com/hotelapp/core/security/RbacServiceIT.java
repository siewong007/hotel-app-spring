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
    void seedSchema() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    is_active BOOLEAN DEFAULT true,
                    is_locked BOOLEAN DEFAULT false,
                    deleted_at TIMESTAMPTZ)""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS refresh_tokens (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_id BIGINT REFERENCES users(id),
                    token_hash TEXT,
                    expires_at TIMESTAMPTZ,
                    revoked_at TIMESTAMPTZ,
                    is_revoked BOOLEAN DEFAULT false)""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS roles (
                    id BIGSERIAL PRIMARY KEY, name TEXT UNIQUE)""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS permissions (
                    id BIGSERIAL PRIMARY KEY, name TEXT UNIQUE)""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS role_permissions (
                    role_id BIGINT REFERENCES roles(id),
                    permission_id BIGINT REFERENCES permissions(id))""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS user_roles (
                    user_id BIGINT REFERENCES users(id),
                    role_id BIGINT REFERENCES roles(id),
                    expires_at TIMESTAMPTZ)""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS teams (
                    id BIGSERIAL PRIMARY KEY,
                    is_active BOOLEAN DEFAULT true,
                    deleted_at TIMESTAMPTZ)""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS team_members (
                    team_id BIGINT REFERENCES teams(id),
                    user_id BIGINT REFERENCES users(id),
                    expires_at TIMESTAMPTZ)""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS team_roles (
                    team_id BIGINT REFERENCES teams(id),
                    role_id BIGINT REFERENCES roles(id))""");
    }

    private long insertUser() {
        return jdbc.queryForObject("INSERT INTO users DEFAULT VALUES RETURNING id", Long.class);
    }

    private long insertRole(String name) {
        return jdbc.queryForObject(
                "INSERT INTO roles(name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name "
                        + "RETURNING id", Long.class, name);
    }

    private long insertPermission(String name) {
        return jdbc.queryForObject(
                "INSERT INTO permissions(name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name "
                        + "RETURNING id", Long.class, name);
    }

    @Test
    void directRoleGrantsPermissionAndManageImpliesActions() {
        long userId = insertUser();
        long roleId = insertRole("front_desk");
        long bookingsRead = insertPermission("bookings:read");
        long roomsManage = insertPermission("rooms:manage");
        jdbc.update("INSERT INTO role_permissions VALUES (?,?)", roleId, bookingsRead);
        jdbc.update("INSERT INTO role_permissions VALUES (?,?)", roleId, roomsManage);
        jdbc.update("INSERT INTO user_roles VALUES (?,?,NULL)", userId, roleId);
        rbacService.invalidateAll();

        assertThat(rbacService.hasPermission(userId, "bookings:read")).isTrue();
        assertThat(rbacService.hasPermission(userId, "bookings:update"))
                .isFalse();
        assertThat(rbacService.hasPermission(userId, "rooms:create")).isTrue();
        assertThat(rbacService.hasRole(userId, "front_desk")).isTrue();
    }

    @Test
    void expiredUserRoleStopsConferringPermissionsImmediately() {
        long userId = insertUser();
        long roleId = insertRole("expired_role");
        long perm = insertPermission("ledgers:read");
        jdbc.update("INSERT INTO role_permissions VALUES (?,?)", roleId, perm);
        jdbc.update(
                "INSERT INTO user_roles VALUES (?,?, NOW() - INTERVAL '1 minute')",
                userId, roleId);
        rbacService.invalidateAll();
        assertThat(rbacService.hasPermission(userId, "ledgers:read")).isFalse();
    }

    @Test
    void activeTeamMembershipConfersTeamRoles() {
        long userId = insertUser();
        long roleId = insertRole("team_viewer");
        long perm = insertPermission("reports:view");
        jdbc.update("INSERT INTO role_permissions VALUES (?,?)", roleId, perm);
        Long teamId = jdbc.queryForObject(
                "INSERT INTO teams (is_active) VALUES (true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO team_members VALUES (?,?,NULL)", teamId, userId);
        jdbc.update("INSERT INTO team_roles VALUES (?,?)", teamId, roleId);
        rbacService.invalidateAll();
        assertThat(rbacService.hasPermission(userId, "reports:view")).isTrue();
    }

    @Test
    void inactiveOrDeletedTeamsDoNotConferRoles() {
        long userId = insertUser();
        long roleId = insertRole("dead_team_role");
        long perm = insertPermission("audit:read");
        jdbc.update("INSERT INTO role_permissions VALUES (?,?)", roleId, perm);
        Long teamId = jdbc.queryForObject(
                "INSERT INTO teams (is_active) VALUES (false) RETURNING id", Long.class);
        jdbc.update("INSERT INTO team_members VALUES (?,?,NULL)", teamId, userId);
        jdbc.update("INSERT INTO team_roles VALUES (?,?)", teamId, roleId);
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
        jdbc.update("INSERT INTO role_permissions VALUES (?,?)", roleId, perm);
        jdbc.update("INSERT INTO user_roles VALUES (?,?,NULL)", userId, roleId);
        rbacService.invalidateAll();

        permissionGate.checkAny(userId, List.of("settings:manage", "settings:read"));

        assertThatThrownBy(() -> permissionGate.checkAny(userId,
                List.of("alpha:x", "beta:y")))
                .isInstanceOfSatisfying(ApiError.class,
                        error -> assertThat(error.message()).isEqualTo(
                                "Missing one of required permissions: alpha:x, beta:y"));
    }
}
