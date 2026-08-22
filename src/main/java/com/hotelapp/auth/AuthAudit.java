package com.hotelapp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthAudit {

    private static final Logger log = LoggerFactory.getLogger(AuthAudit.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuthAudit(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.objectMapper = objectMapper;
    }

    public void loginSuccess(long userId, String method, String ipAddress, String userAgent) {
        write(userId, "login_success", "user", userId,
                mapOf("method", method, "success", true), ipAddress, userAgent);
    }

    public void loginFailure(String username, String reason, String ipAddress, String userAgent) {
        write(null, "login_failure", "user", null,
                mapOf("username", username, "reason", reason, "success", false), ipAddress, userAgent);
    }

    private void write(Long userId, String action, String resourceType, Long resourceId,
            java.util.Map<String, Object> details, String ipAddress, String userAgent) {
        try {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("action", action)
                    .addValue("resourceType", resourceType)
                    .addValue("resourceId", resourceId)
                    .addValue("details", objectMapper.writeValueAsString(details))
                    .addValue("ipAddress", ipAddress)
                    .addValue("userAgent", userAgent);
            jdbc.update("""
                    INSERT INTO audit_logs
                    (user_id, action, resource_type, resource_id, details, ip_address, user_agent, created_at)
                    VALUES (:userId, :action, :resourceType, :resourceId,
                            CAST(:details AS jsonb), CAST(:ipAddress AS inet), :userAgent, :createdAt)
                    """, params.addValue("createdAt", OffsetDateTime.now()));
        } catch (Exception e) {
            log.warn("Audit log failed: {} - Action: {}, Resource: {}", e.getMessage(), action,
                    resourceType);
        }
    }

    private static java.util.Map<String, Object> mapOf(Object... pairs) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
