package com.hotelapp.core.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DbActiveSessionValidator implements ActiveSessionValidator {

    private static final String SQL = """
            SELECT EXISTS(
                SELECT 1
                FROM refresh_tokens AS session
                INNER JOIN users AS account ON account.id = session.user_id
                WHERE session.id = cast(? as uuid) AND session.user_id = ?
                  AND session.expires_at > CURRENT_TIMESTAMP
                  AND session.revoked_at IS NULL AND session.is_revoked = false
                  AND account.is_active = true
                  AND account.is_locked = false
                  AND account.deleted_at IS NULL
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public DbActiveSessionValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isActive(long userId, String sessionId) {
        Boolean exists = jdbcTemplate.queryForObject(SQL, Boolean.class, sessionId, userId);
        return Boolean.TRUE.equals(exists);
    }
}
