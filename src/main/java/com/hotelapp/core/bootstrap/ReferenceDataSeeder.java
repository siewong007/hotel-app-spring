package com.hotelapp.core.bootstrap;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Port of database/postgres/seed.sql: reference data always applies
 * idempotently; sample property data only lands on an empty catalogue; the
 * seeded accounts use a non-recoverable placeholder bcrypt hash whose real
 * password is set with the fix_password helper.
 */
@Component
public class ReferenceDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSeeder.class);

    private static final String ADMIN_PASSWORD_HASH =
            "$2b$12$Fq3zPzZ.mr/wuYrbUPUItOqoC9YvsFfW.mcq4B6U5e3nWsPr4JQdK";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ReferenceDataSeeder(JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public synchronized void seed() {
        transactionTemplate.executeWithoutResult(status -> {
            seedReferenceData();
            seedUsers();
            seedSampleData();
        });
    }

    private boolean alreadySeeded() {
        Integer roles = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        return roles != null && roles > 0;
    }

    private void seedReferenceData() {
        if (alreadySeeded()) {
            log.info("[seed] reference data present; skipping");
            return;
        }
        int statements = 0;
        for (String statement : splitStatements("db/reference-data.sql")) {
            try {
                jdbcTemplate.execute(statement);
            } catch (Exception e) {
                String snippet = statement.length() > 120
                        ? statement.substring(0, 120) + "..." : statement;
                log.error("[seed] failed statement: {}", snippet, e);
                throw e;
            }
            statements++;
        }
        log.info("[seed] applied {} reference-data statements", statements);
    }

    private void seedUsers() {
        jdbcTemplate.update("""
                INSERT INTO users (id, username, email, password_hash, full_name, is_active, is_verified, is_super_admin)
                VALUES (1000, 'admin', 'admin@hotel.com', ?, 'System Administrator', true, true, true)
                ON CONFLICT (username) DO NOTHING
                """, ADMIN_PASSWORD_HASH);
        jdbcTemplate.update("""
                INSERT INTO users (username, email, password_hash, full_name, is_active, is_verified, is_super_admin)
                VALUES ('superadmin', 'superadmin@hotel.local', ?, 'Super Administrator', true, true, true)
                ON CONFLICT (username) DO NOTHING
                """, ADMIN_PASSWORD_HASH);
        jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'admin'
                WHERE u.username = 'admin'
                ON CONFLICT DO NOTHING
                """);
        jdbcTemplate.update("""
                INSERT INTO user_roles (user_id, role_id)
                SELECT u.id, r.id FROM users u JOIN roles r ON r.name = 'super_admin'
                WHERE u.username = 'superadmin'
                ON CONFLICT DO NOTHING
                """);
        jdbcTemplate.execute("""
                SELECT setval('users_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1000) FROM users), 1000) + 1, false)
                """);
        log.info("[seed] seeded accounts admin/superadmin (placeholder passwords)");
    }

    private void seedSampleData() {
        Integer roomTypes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_types", Integer.class);
        if (roomTypes != null && roomTypes > 0) {
            log.info("[seed] existing property catalogue preserved; sample data not loaded");
            return;
        }
        int statements = 0;
        for (String statement : splitStatements("db/sample-data.sql")) {
            jdbcTemplate.execute(statement);
            statements++;
        }
        log.info("[seed] loaded sample property catalogue ({} statements)", statements);
    }

    static List<String> splitStatements(String resource) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        try (var reader = new ClassPathResource(resource).getInputStream()) {
            String content = new String(reader.readAllBytes());
            int depth = 0;
            for (char c : content.toCharArray()) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                }
                current.append(c);
                if (c == ';' && depth == 0) {
                    String statement = current.toString().trim();
                    if (!statement.isEmpty()) {
                        statements.add(statement);
                    }
                    current.setLength(0);
                }
            }
            String tail = current.toString().trim();
            if (!tail.isEmpty()) {
                statements.add(tail);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed reading " + resource, e);
        }
        return statements;
    }
}
