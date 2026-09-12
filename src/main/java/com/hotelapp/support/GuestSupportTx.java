package com.hotelapp.support;

import com.hotelapp.core.error.ApiError;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction-scoped support writes — isolated on its own bean so the
 * {@code @Transactional} proxy actually applies ({@code insert_conversation}
 * + idempotency key + first message + event; the mutation update + message +
 * event for replies/reopens).
 */
@Component
public class GuestSupportTx {

    private final JdbcTemplate jdbc;

    public GuestSupportTx(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** {@code ConversationMutation} — every mutable conversation column. */
    public record Mutation(
            String status,
            String priority,
            String assignedTeam,
            Long assignedToUserId,
            int escalationLevel,
            OffsetDateTime escalatedAt,
            OffsetDateTime firstResponseDueAt,
            OffsetDateTime resolutionDueAt,
            OffsetDateTime firstResponseAt,
            OffsetDateTime resolvedAt,
            OffsetDateTime closedAt,
            String resolutionCode,
            String resolutionSummary,
            int reopenCount,
            Long expectedVersion) {

        Mutation withStatus(String newStatus) {
            return new Mutation(newStatus, priority, assignedTeam, assignedToUserId,
                    escalationLevel, escalatedAt, firstResponseDueAt, resolutionDueAt,
                    firstResponseAt, resolvedAt, closedAt, resolutionCode, resolutionSummary,
                    reopenCount, expectedVersion);
        }

        Mutation withResolutionDueAt(OffsetDateTime dueAt) {
            return new Mutation(status, priority, assignedTeam, assignedToUserId,
                    escalationLevel, escalatedAt, firstResponseDueAt, dueAt,
                    firstResponseAt, resolvedAt, closedAt, resolutionCode, resolutionSummary,
                    reopenCount, expectedVersion);
        }

        Mutation withExpectedVersion(Long version) {
            return new Mutation(status, priority, assignedTeam, assignedToUserId,
                    escalationLevel, escalatedAt, firstResponseDueAt, resolutionDueAt,
                    firstResponseAt, resolvedAt, closedAt, resolutionCode, resolutionSummary,
                    reopenCount, version);
        }

        /** The resolved → waiting_for_staff transition shared by reply and reopen. */
        Mutation reopen(OffsetDateTime resolutionDueAt) {
            return new Mutation("waiting_for_staff", priority, assignedTeam, assignedToUserId,
                    escalationLevel, escalatedAt, firstResponseDueAt, resolutionDueAt,
                    firstResponseAt, null, null, null, null,
                    reopenCount + 1, expectedVersion);
        }
    }

    /** {@code NewConversation} — the guest create payload. */
    public record NewConversation(
            String conversationNumber,
            long guestId,
            Long bookingId,
            String subject,
            String category,
            String priority,
            OffsetDateTime firstResponseDueAt,
            OffsetDateTime resolutionDueAt) {
    }

    /**
     * {@code create_guest_conversation}'s tx body: conversation row, the
     * guest-request idempotency key, the opening message and the
     * {@code created} event. Returns the new conversation id, or {@code null}
     * when the idempotency key was already consumed (concurrent duplicate).
     */
    @Transactional
    public Long createConversation(NewConversation conversation, long guestId,
            String clientRequestId, String message) {
        Long conversationId = jdbc.queryForObject("""
                INSERT INTO support_conversations (
                    conversation_number, guest_id, booking_id, subject, category, priority,
                    first_response_due_at, resolution_due_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, conversation.conversationNumber(), conversation.guestId(),
                conversation.bookingId(), conversation.subject(), conversation.category(),
                conversation.priority(), conversation.firstResponseDueAt(),
                conversation.resolutionDueAt());

        int keyInserted = jdbc.update("""
                INSERT INTO support_guest_request_idempotency_keys
                    (guest_id, idempotency_key, conversation_id)
                VALUES (?, ?, ?)
                ON CONFLICT (guest_id, idempotency_key) DO NOTHING
                """, guestId, clientRequestId, conversationId);
        if (keyInserted != 1) {
            // Roll the transaction back so the new conversation row dies with it.
            throw new DuplicateRequest();
        }

        jdbc.update("""
                INSERT INTO support_messages (
                    conversation_id, author_type, author_guest_id, author_user_id, body,
                    client_message_id)
                VALUES (?, 'guest', ?, NULL, ?, ?)
                """, conversationId, guestId, message, clientRequestId);
        insertEvent(conversationId, guestId, null, "created", null, "waiting_for_staff",
                "{\"category\": " + jsonString(conversation.category()) + "}");
        return conversationId;
    }

    /** Marks the idempotent-replay rollback path out of {@link #createConversation}. */
    public static final class DuplicateRequest extends RuntimeException {
        DuplicateRequest() {
            super(null, null, false, false);
        }
    }

    /**
     * {@code send_guest_message}/{@code reopen_guest_conversation}'s tx body:
     * optimistic version-checked update, then the message/event rows.
     */
    @Transactional
    public boolean mutateConversation(long conversationId, Mutation mutation, Long guestId,
            String message, String clientMessageId, String eventType, String fromStatus,
            String detailsJson) {
        int updated = jdbc.update("""
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
                mutation.expectedVersion());
        if (updated != 1) {
            return false;
        }
        if (message != null) {
            jdbc.update("""
                    INSERT INTO support_messages (
                        conversation_id, author_type, author_guest_id, author_user_id, body,
                        client_message_id)
                    VALUES (?, 'guest', ?, NULL, ?, ?)
                    """, conversationId, guestId, message, clientMessageId);
        }
        insertEvent(conversationId, guestId, null, eventType, fromStatus, mutation.status(),
                detailsJson);
        return true;
    }

    void insertEvent(long conversationId, Long actorGuestId, Long actorUserId, String eventType,
            String fromStatus, String toStatus, String detailsJson) {
        jdbc.update("""
                INSERT INTO support_events (
                    conversation_id, actor_guest_id, actor_user_id, event_type, from_status,
                    to_status, details)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
                """, conversationId, actorGuestId, actorUserId, eventType, fromStatus, toStatus,
                detailsJson);
    }

    private static String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Look up the conversation id a guest request key already produced. */
    public Long findGuestRequestConversation(long guestId, String clientRequestId) {
        List<Long> ids = jdbc.query("""
                SELECT conversation_id FROM support_guest_request_idempotency_keys
                WHERE guest_id = ? AND idempotency_key = ?
                """, (rs, i) -> rs.getLong(1), guestId, clientRequestId);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
