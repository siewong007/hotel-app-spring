package com.hotelapp.support;

import com.hotelapp.support.GuestSupportTx.Mutation;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Transaction-scoped staff support writes — mirrors upstream's
 * {@code pool.begin()} bodies for {@code send_staff_message} and
 * {@code apply_staff_action}: version-guarded conversation update + message /
 * event / action-key rows atomically.
 */
@Component
public class StaffSupportTx {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StaffSupportTx(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** {@code SupportRepository::update_conversation} — returns false on a stale version. */
    private boolean updateConversation(long conversationId, Mutation mutation) {
        return jdbc.update("""
                UPDATE support_conversations
                SET status = ?, priority = ?, assigned_team = ?, assigned_to_user_id = ?,
                    escalation_level = ?, escalated_at = ?, first_response_due_at = ?,
                    resolution_due_at = ?, first_response_at = ?, resolved_at = ?,
                    closed_at = ?, resolution_code = ?, resolution_summary = ?,
                    reopen_count = ?, version = version + 1,
                    last_activity_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND (?::bigint IS NULL OR version = ?)
                """, mutation.status(), mutation.priority(), mutation.assignedTeam(),
                mutation.assignedToUserId(), (short) mutation.escalationLevel(),
                mutation.escalatedAt(), mutation.firstResponseDueAt(),
                mutation.resolutionDueAt(), mutation.firstResponseAt(), mutation.resolvedAt(),
                mutation.closedAt(), mutation.resolutionCode(), mutation.resolutionSummary(),
                mutation.reopenCount(), conversationId, mutation.expectedVersion(),
                mutation.expectedVersion()) == 1;
    }

    /** {@code insert_message} for a staff author. */
    private void insertStaffMessage(long conversationId, long actorId, String body,
            String clientMessageId) {
        jdbc.update("""
                INSERT INTO support_messages (
                    conversation_id, author_type, author_guest_id, author_user_id, body,
                    client_message_id)
                VALUES (?, 'staff', NULL, ?, ?, ?)
                """, conversationId, actorId, body, clientMessageId);
    }

    private void insertEvent(long conversationId, long actorUserId, String eventType,
            String fromStatus, String toStatus, Map<String, Object> details) {
        String detailsJson = null;
        if (details != null) {
            try {
                detailsJson = objectMapper.writeValueAsString(details);
            } catch (Exception e) {
                detailsJson = null;
            }
        }
        jdbc.update("""
                INSERT INTO support_events (
                    conversation_id, actor_guest_id, actor_user_id, event_type, from_status,
                    to_status, details)
                VALUES (?, NULL, ?, ?, ?, ?, CAST(? AS jsonb))
                """, conversationId, actorUserId, eventType, fromStatus, toStatus, detailsJson);
    }

    /** {@code send_staff_message}'s tx body: update + staff message + event. */
    @Transactional
    public boolean applyMutation(long conversationId, Mutation mutation, String body,
            long actorId, String clientMessageId, String eventType, String fromStatus,
            Map<String, Object> details) {
        if (!updateConversation(conversationId, mutation)) {
            return false;
        }
        insertStaffMessage(conversationId, actorId, body, clientMessageId);
        insertEvent(conversationId, actorId, eventType, fromStatus, mutation.status(), details);
        return true;
    }

    /** {@code find_action_key_action} — the stored action for a replayed key. */
    public String findActionKeyAction(long conversationId, long actorId, String key) {
        List<String> results = jdbc.query("""
                SELECT action FROM support_action_idempotency_keys
                WHERE conversation_id = ? AND actor_user_id = ? AND idempotency_key = ?
                """, (rs, i) -> rs.getString(1), conversationId, actorId, key);
        return results.isEmpty() ? null : results.get(0);
    }

    /** {@code apply_staff_action}'s tx body: update + event + action key. */
    @Transactional
    public boolean applyAction(long conversationId, Mutation mutation, long actorId,
            String eventType, String fromStatus, Map<String, Object> details,
            String clientActionId, String action) {
        if (!updateConversation(conversationId, mutation)) {
            return false;
        }
        insertEvent(conversationId, actorId, eventType, fromStatus, mutation.status(), details);
        if (clientActionId != null) {
            jdbc.update("""
                    INSERT INTO support_action_idempotency_keys
                        (conversation_id, actor_user_id, idempotency_key, action)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (conversation_id, actor_user_id, idempotency_key) DO NOTHING
                    """, conversationId, actorId, clientActionId, action);
        }
        return true;
    }
}
