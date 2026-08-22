package com.hotelapp.core.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of services/audit.rs AuditLog::log_event - every mutating handler writes
 * an audit row. Failures never break the business operation.
 */
@Component
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditWriter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void event(Long userId, String action, String resourceType, Long resourceId,
            Object details) {
        try {
            jdbc.update("""
                    INSERT INTO audit_logs (user_id, action, resource_type, resource_id, details)
                    VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                    """, userId, action, resourceType, resourceId,
                    details == null ? null : objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            log.warn("Audit log failed: {} - Action: {}, Resource: {}", e.getMessage(), action,
                    resourceType);
        }
    }

    public void loginFailure(String username, String reason) {
        event(null, "login_failure", "user", null,
                java.util.Map.of("username", username, "reason", reason, "success", false));
    }
}
