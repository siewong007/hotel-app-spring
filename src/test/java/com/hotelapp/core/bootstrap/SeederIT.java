package com.hotelapp.core.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.jwt-secret=unit-test-secret-key-that-is-long-enough-32ch")
@Testcontainers
class SeederIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ReferenceDataSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedsReferenceAndSampleDataIdempotently() {
        seeder.seed();

        Integer roles = count("roles");
        Integer permissions = count("permissions");
        Integer policies = count("route_access_policies");
        Integer settings = count("system_settings");
        assertThat(roles).isEqualTo(12);
        assertThat(permissions).isGreaterThan(40);
        assertThat(policies).isGreaterThanOrEqualTo(5);
        assertThat(settings).isGreaterThan(10);
        assertThat(setting("timezone")).isEqualTo("Asia/Kuala_Lumpur");
        assertThat(count("room_types")).isEqualTo(4);
        assertThat(count("rooms")).isEqualTo(16);
        assertThat(count("rate_plans")).isEqualTo(6);

        Map<String, Object> admin = jdbc.queryForMap(
                "SELECT * FROM users WHERE username = 'admin'");
        assertThat(admin.get("id")).isEqualTo(1000L);
        assertThat(admin.get("is_super_admin")).isEqualTo(true);
        assertThat(countWhere("user_roles",
                "user_id = 1000 AND role_id IN (SELECT id FROM roles WHERE name = 'admin')"))
                .isEqualTo(1);

        int beforeRoles = roles;
        int beforeRooms = count("rooms");
        seeder.seed();
        assertThat(count("roles")).isEqualTo(beforeRoles);
        assertThat(count("rooms")).isEqualTo(beforeRooms);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private int countWhere(String table, String where) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + where, Integer.class);
        return value == null ? 0 : value;
    }

    private String setting(String key) {
        return jdbc.queryForObject(
                "SELECT value FROM system_settings WHERE key = ?", String.class, key);
    }
}
