package com.hotelapp.support;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.RbacService;
import com.hotelapp.support.GuestSupport.ConversationRow;
import com.hotelapp.support.GuestSupportTx.Mutation;
import com.hotelapp.support.SupportModels.StaffConversation;
import com.hotelapp.support.SupportModels.StaffEvent;
import com.hotelapp.support.SupportModels.StaffMessage;
import com.hotelapp.support.SupportModels.SupportActionRequest;
import com.hotelapp.support.SupportModels.SupportAgent;
import com.hotelapp.support.SupportModels.SupportConversationDetail;
import com.hotelapp.support.SupportModels.SupportConversationListResponse;
import com.hotelapp.support.SupportModels.SupportMessageRequest;
import com.hotelapp.support.SupportModels.SupportQueueMetrics;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of the staff slice of {@code modules/support/service.rs} +
 * {@code repository.rs}: the support queue list/metrics/agents reads plus
 * {@code send_staff_message} and {@code apply_staff_action} with their
 * version guards, idempotency keys and per-action permission map.
 */
@Component
public class StaffSupport {

    private final JdbcTemplate jdbc;
    private final GuestSupport guestSupport;
    private final StaffSupportTx tx;
    private final SupportHub hub;
    private final RbacService rbac;
    private final AuditWriter audit;

    public StaffSupport(JdbcTemplate jdbc, GuestSupport guestSupport, StaffSupportTx tx,
            SupportHub hub, RbacService rbac, AuditWriter audit) {
        this.jdbc = jdbc;
        this.guestSupport = guestSupport;
        this.tx = tx;
        this.hub = hub;
        this.rbac = rbac;
        this.audit = audit;
    }

    // ------------------------------------------------------------------
    // Guards + helpers
    // ------------------------------------------------------------------

    /** {@code can_manage}: support:manage without throwing on denial. */
    private boolean canManage(long userId) {
        return rbac.hasPermission(userId, "support:manage");
    }

    /** {@code require_action_permission}: the per-action permission map. */
    private void requireActionPermission(long userId, String action) {
        String permission = switch (action) {
            case "claim", "assign", "release" -> "support:assign";
            case "escalate" -> "support:escalate";
            case "set_priority", "close", "reopen" -> "support:manage";
            case "resolve", "add_internal_note" -> "support:write";
            default -> throw ApiError.badRequest("Unsupported support action");
        };
        com.hotelapp.core.security.PermissionGateHelper.check(userId, permission);
    }

    private static void ensureOwnerOrManager(Long assignedToUserId, long actorId,
            boolean isManager) {
        if (isManager || (assignedToUserId != null && assignedToUserId == actorId)) {
            return;
        }
        if (assignedToUserId == null) {
            throw ApiError.conflict("Claim this conversation before taking that action");
        }
        throw ApiError.forbidden(
                "This conversation is assigned to another staff member");
    }

    private static boolean isActiveStatus(String status) {
        return "waiting_for_staff".equals(status) || "waiting_for_guest".equals(status);
    }

    /** {@code rebase_sla_due_at}: shift an open SLA by the priority delta. */
    static OffsetDateTime rebaseSlaDueAt(OffsetDateTime dueAt, long previousMinutes,
            long nextMinutes) {
        return dueAt == null ? null
                : dueAt.minus(Duration.ofMinutes(previousMinutes))
                        .plus(Duration.ofMinutes(nextMinutes));
    }

    // ------------------------------------------------------------------
    // Repository: staff reads
    // ------------------------------------------------------------------

    /**
     * {@code CONVERSATION_SELECT} — the staff projection joining guest,
     * booking, room and assignee names plus the latest-message subqueries.
     */
    private static final String CONVERSATION_SELECT = """
            SELECT
                c.id, c.conversation_number, c.guest_id,
                COALESCE(g.nick_name, trim(g.first_name || ' ' || g.last_name), 'Guest')
                    AS guest_name,
                g.email AS guest_email, c.booking_id, b.booking_number AS booking_reference,
                r.room_number, c.category, c.status, c.priority, c.assigned_team,
                c.assigned_to_user_id, u.full_name AS assigned_to_name, c.escalation_level,
                c.escalated_at, c.first_response_due_at, c.resolution_due_at,
                c.first_response_at, c.resolved_at, c.closed_at, c.subject,
                b.status AS stay_status, b.check_in_date, b.check_out_date,
                c.resolution_code, c.resolution_summary, c.reopen_count, c.version,
                c.last_activity_at, c.created_at, c.updated_at,
                (SELECT sm.body FROM support_messages sm
                 WHERE sm.conversation_id = c.id
                 ORDER BY sm.created_at DESC, sm.id DESC LIMIT 1) AS last_message_preview,
                (SELECT sm.created_at FROM support_messages sm
                 WHERE sm.conversation_id = c.id
                 ORDER BY sm.created_at DESC, sm.id DESC LIMIT 1) AS last_message_at
            FROM support_conversations c
            JOIN guests g ON g.id = c.guest_id
            LEFT JOIN bookings b ON b.id = c.booking_id
            LEFT JOIN rooms r ON r.id = b.room_id
            LEFT JOIN users u ON u.id = c.assigned_to_user_id
            """;

    /** {@code summary_from_row}: SLA risk/breach flags computed at read time. */
    static StaffConversation staffConversationFromRow(Map<String, Object> row) {
        OffsetDateTime firstResponseDueAt = (OffsetDateTime) row.get("first_response_due_at");
        OffsetDateTime resolutionDueAt = (OffsetDateTime) row.get("resolution_due_at");
        OffsetDateTime firstResponseAt = (OffsetDateTime) row.get("first_response_at");
        OffsetDateTime resolvedAt = (OffsetDateTime) row.get("resolved_at");
        String status = (String) row.get("status");
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime activeDue;
        if (firstResponseAt == null && "waiting_for_staff".equals(status)) {
            activeDue = firstResponseDueAt;
        } else if (resolvedAt == null && !"waiting_for_guest".equals(status)
                && !"closed".equals(status)) {
            activeDue = resolutionDueAt;
        } else {
            activeDue = null;
        }
        boolean breached = activeDue != null && !activeDue.isAfter(now);
        boolean atRisk = !breached && activeDue != null
                && !activeDue.isAfter(now.plusMinutes(30));
        return new StaffConversation(
                ((Number) row.get("id")).longValue(),
                (String) row.get("conversation_number"),
                ((Number) row.get("guest_id")).longValue(),
                (String) row.get("guest_name"),
                (String) row.get("guest_email"),
                row.get("booking_id") instanceof Number n ? n.longValue() : null,
                (String) row.get("booking_reference"),
                (String) row.get("room_number"),
                (String) row.get("category"),
                status,
                (String) row.get("priority"),
                (String) row.get("assigned_team"),
                row.get("assigned_to_user_id") instanceof Number n ? n.longValue() : null,
                (String) row.get("assigned_to_name"),
                row.get("escalation_level") instanceof Number n ? n.intValue() : 0,
                (OffsetDateTime) row.get("escalated_at"),
                firstResponseDueAt,
                resolutionDueAt,
                firstResponseAt,
                resolvedAt,
                (OffsetDateTime) row.get("closed_at"),
                (String) row.get("last_message_preview"),
                (OffsetDateTime) row.get("last_message_at"),
                (OffsetDateTime) row.get("last_activity_at"),
                0,
                atRisk,
                breached,
                row.get("version") instanceof Number n ? n.longValue() : 0,
                (String) row.get("subject"),
                (String) row.get("stay_status"),
                row.get("check_in_date") instanceof java.sql.Date d ? d.toLocalDate() : null,
                row.get("check_out_date") instanceof java.sql.Date d ? d.toLocalDate() : null,
                (String) row.get("resolution_code"),
                (String) row.get("resolution_summary"),
                row.get("reopen_count") instanceof Number n ? n.intValue() : 0,
                (OffsetDateTime) row.get("created_at"),
                (OffsetDateTime) row.get("updated_at"));
    }

    private StaffConversation findStaffConversation(long conversationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                CONVERSATION_SELECT + " WHERE c.id = ?", conversationId);
        return rows.isEmpty() ? null : staffConversationFromRow(rows.get(0));
    }

    private List<StaffMessage> listStaffMessages(long conversationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT sm.id, sm.conversation_id, sm.author_type, sm.author_user_id,
                    sm.author_guest_id,
                    COALESCE(u.full_name, g.nick_name,
                        trim(g.first_name || ' ' || g.last_name)) AS author_name,
                    sm.body, sm.created_at
                FROM support_messages sm
                LEFT JOIN users u ON u.id = sm.author_user_id
                LEFT JOIN guests g ON g.id = sm.author_guest_id
                WHERE sm.conversation_id = ?
                ORDER BY sm.created_at ASC, sm.id ASC
                """, conversationId);
        List<StaffMessage> messages = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            messages.add(new StaffMessage(
                    ((Number) row.get("id")).longValue(),
                    ((Number) row.get("conversation_id")).longValue(),
                    (String) row.get("author_type"),
                    row.get("author_user_id") instanceof Number n ? n.longValue() : null,
                    row.get("author_guest_id") instanceof Number n ? n.longValue() : null,
                    (String) row.get("author_name"),
                    (String) row.get("body"),
                    (OffsetDateTime) row.get("created_at")));
        }
        return messages;
    }

    private List<StaffEvent> listStaffEvents(long conversationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT se.id, se.conversation_id, se.event_type, se.actor_user_id,
                    u.full_name AS actor_name, se.details, se.created_at
                FROM support_events se
                LEFT JOIN users u ON u.id = se.actor_user_id
                WHERE se.conversation_id = ?
                ORDER BY se.created_at ASC, se.id ASC
                """, conversationId);
        List<StaffEvent> events = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object details = row.get("details");
            String body = null;
            if (details instanceof Map<?, ?> map && map.get("body") instanceof String s) {
                body = s;
            }
            events.add(new StaffEvent(
                    ((Number) row.get("id")).longValue(),
                    ((Number) row.get("conversation_id")).longValue(),
                    (String) row.get("event_type"),
                    row.get("actor_user_id") instanceof Number n ? n.longValue() : null,
                    (String) row.get("actor_name"),
                    body,
                    details,
                    (OffsetDateTime) row.get("created_at")));
        }
        return events;
    }

    /** {@code staff_detail}. */
    public SupportConversationDetail staffDetail(long conversationId) {
        StaffConversation conversation = findStaffConversation(conversationId);
        if (conversation == null) {
            throw ApiError.notFound("Support conversation not found");
        }
        return new SupportConversationDetail(conversation,
                listStaffMessages(conversationId), listStaffEvents(conversationId));
    }

    private ConversationRow findMutationRow(long conversationId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.id, c.category, c.status, c.priority, c.assigned_team, c.booking_id,
                    c.subject, c.assigned_to_user_id, c.escalation_level, c.escalated_at,
                    c.first_response_due_at, c.resolution_due_at, c.first_response_at,
                    c.resolved_at, c.closed_at, c.resolution_code, c.resolution_summary,
                    c.reopen_count, c.created_at, c.updated_at, c.last_activity_at, c.version,
                    c.guest_id
                FROM support_conversations c WHERE c.id = ?
                """, conversationId);
        return rows.isEmpty() ? null : conversationFromRow(rows.get(0));
    }

    private static ConversationRow conversationFromRow(Map<String, Object> row) {
        return new ConversationRow(
                ((Number) row.get("id")).longValue(),
                (String) row.get("category"),
                (String) row.get("status"),
                (String) row.get("priority"),
                (String) row.get("assigned_team"),
                row.get("booking_id") instanceof Number n ? n.longValue() : null,
                (String) row.get("subject"),
                row.get("assigned_to_user_id") instanceof Number n ? n.longValue() : null,
                row.get("escalation_level") instanceof Number n ? n.intValue() : 0,
                (OffsetDateTime) row.get("escalated_at"),
                (OffsetDateTime) row.get("first_response_due_at"),
                (OffsetDateTime) row.get("resolution_due_at"),
                (OffsetDateTime) row.get("first_response_at"),
                (OffsetDateTime) row.get("resolved_at"),
                (OffsetDateTime) row.get("closed_at"),
                (String) row.get("resolution_code"),
                (String) row.get("resolution_summary"),
                row.get("reopen_count") instanceof Number n ? n.intValue() : 0,
                (OffsetDateTime) row.get("created_at"),
                (OffsetDateTime) row.get("updated_at"),
                (OffsetDateTime) row.get("last_activity_at"),
                row.get("version") instanceof Number n ? n.longValue() : 0);
    }

    // ------------------------------------------------------------------
    // Service: staff reads
    // ------------------------------------------------------------------

    /** {@code list_staff_conversations} — filtered queue page + metrics. */
    public SupportConversationListResponse listStaffConversations(long actorId,
            String queue, String status, String priority, Long assignedToUserId,
            String search, Long page, Long pageSize) {
        if (status != null) {
            GuestSupport.validateStatus(status);
        }
        if (priority != null) {
            GuestSupport.validatePriority(priority);
        }
        if (queue != null && !GuestSupport.SUPPORT_QUEUES.contains(
                GuestSupport.normalizedChoice(queue))) {
            throw ApiError.badRequest("Unsupported support queue");
        }
        String normalizedQueue = queue == null ? null : GuestSupport.normalizedChoice(queue);
        String normalizedStatus = status == null ? null : GuestSupport.normalizedChoice(status);
        String normalizedPriority = priority == null ? null
                : GuestSupport.normalizedChoice(priority);
        long p = page == null ? 1 : Math.max(page, 1);
        long size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        long offset = (p - 1) * size;
        String searchPattern = search == null || search.trim().isEmpty() ? null
                : "%" + search.trim() + "%";

        String filter = """
                WHERE (
                        ?::text IS NULL
                        OR (? = 'unassigned' AND c.assigned_to_user_id IS NULL
                            AND c.status <> 'closed')
                        OR (? = 'mine' AND c.assigned_to_user_id = ? AND c.status <> 'closed')
                        OR (? = 'at_risk' AND (
                            (c.first_response_at IS NULL AND c.status = 'waiting_for_staff'
                             AND c.first_response_due_at <= CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                            OR (c.resolved_at IS NULL
                             AND c.status NOT IN ('waiting_for_guest', 'closed')
                             AND c.resolution_due_at <= CURRENT_TIMESTAMP + INTERVAL '30 minutes')))
                        OR (? IN ('waiting_for_staff', 'waiting_for_guest', 'resolved', 'closed')
                            AND c.status = ?)
                    )
                  AND (?::text IS NULL OR c.status = ?)
                  AND (?::text IS NULL OR c.priority = ?)
                  AND (?::bigint IS NULL OR c.assigned_to_user_id = ?)
                  AND (?::text IS NULL OR (
                        lower(c.conversation_number) LIKE lower(?)
                        OR lower(COALESCE(g.nick_name, g.first_name || ' ' || g.last_name, ''))
                            LIKE lower(?)
                        OR lower(COALESCE(b.booking_number, '')) LIKE lower(?)))
                """;
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_conversations c"
                        + " JOIN guests g ON g.id = c.guest_id"
                        + " LEFT JOIN bookings b ON b.id = c.booking_id " + filter,
                Long.class, normalizedQueue, normalizedQueue, normalizedQueue, actorId,
                normalizedQueue, normalizedQueue, normalizedStatus, normalizedStatus,
                normalizedPriority, normalizedPriority, assignedToUserId, assignedToUserId,
                searchPattern, searchPattern, searchPattern, searchPattern);
        List<Map<String, Object>> rows = jdbc.queryForList(CONVERSATION_SELECT + filter + """
                ORDER BY CASE c.priority WHEN 'urgent' THEN 4 WHEN 'high' THEN 3
                         WHEN 'normal' THEN 2 ELSE 1 END DESC,
                         c.first_response_due_at ASC NULLS LAST, c.last_activity_at DESC
                LIMIT ? OFFSET ?
                """, normalizedQueue, normalizedQueue, normalizedQueue, actorId,
                normalizedQueue, normalizedQueue, normalizedStatus, normalizedStatus,
                normalizedPriority, normalizedPriority, assignedToUserId, assignedToUserId,
                searchPattern, searchPattern, searchPattern, searchPattern, size, offset);
        List<StaffConversation> items = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            items.add(staffConversationFromRow(row));
        }
        return new SupportConversationListResponse(items, total, p, size, queueMetrics());
    }

    /** {@code queue_metrics}. */
    public SupportQueueMetrics queueMetrics() {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT
                    COUNT(*) FILTER (WHERE status <> 'closed') AS total_open,
                    COUNT(*) FILTER (WHERE status <> 'closed'
                        AND assigned_to_user_id IS NULL) AS unassigned,
                    COUNT(*) FILTER (WHERE status = 'waiting_for_staff') AS waiting_for_staff,
                    COUNT(*) FILTER (WHERE status = 'waiting_for_guest') AS waiting_for_guest,
                    COUNT(*) FILTER (WHERE (
                        (first_response_at IS NULL AND status = 'waiting_for_staff'
                         AND first_response_due_at <= CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                        OR (resolved_at IS NULL
                         AND status NOT IN ('waiting_for_guest', 'closed')
                         AND resolution_due_at <= CURRENT_TIMESTAMP + INTERVAL '30 minutes')
                    )) AS at_risk,
                    COUNT(*) FILTER (WHERE (
                        (first_response_at IS NULL AND status = 'waiting_for_staff'
                         AND first_response_due_at <= CURRENT_TIMESTAMP)
                        OR (resolved_at IS NULL
                         AND status NOT IN ('waiting_for_guest', 'closed')
                         AND resolution_due_at <= CURRENT_TIMESTAMP)
                    )) AS breached
                FROM support_conversations
                """);
        return new SupportQueueMetrics(
                row.get("total_open") instanceof Number n ? n.longValue() : 0,
                row.get("unassigned") instanceof Number n ? n.longValue() : 0,
                row.get("waiting_for_staff") instanceof Number n ? n.longValue() : 0,
                row.get("waiting_for_guest") instanceof Number n ? n.longValue() : 0,
                row.get("at_risk") instanceof Number n ? n.longValue() : 0,
                row.get("breached") instanceof Number n ? n.longValue() : 0);
    }

    /** {@code list_agents}: active users holding read+write (or manage). */
    public List<SupportAgent> listSupportAgents() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DISTINCT u.id, COALESCE(u.full_name, u.username) AS name, u.email
                FROM users u
                WHERE COALESCE(u.is_active, true) = true
                  AND EXISTS (
                      SELECT 1 FROM user_roles ur
                      JOIN role_permissions rp ON rp.role_id = ur.role_id
                      JOIN permissions p ON p.id = rp.permission_id
                      WHERE ur.user_id = u.id
                        AND p.name IN ('support:read', 'support:manage'))
                  AND EXISTS (
                      SELECT 1 FROM user_roles ur
                      JOIN role_permissions rp ON rp.role_id = ur.role_id
                      JOIN permissions p ON p.id = rp.permission_id
                      WHERE ur.user_id = u.id
                        AND p.name IN ('support:write', 'support:manage'))
                ORDER BY name ASC
                """);
        List<SupportAgent> agents = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            agents.add(new SupportAgent(((Number) row.get("id")).longValue(),
                    (String) row.get("name"), (String) row.get("email"), true));
        }
        return agents;
    }

    /** {@code is_active_support_agent}. */
    public boolean isActiveSupportAgent(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM users u
                    WHERE u.id = ? AND COALESCE(u.is_active, true) = true
                      AND EXISTS (
                          SELECT 1 FROM user_roles ur
                          JOIN role_permissions rp ON rp.role_id = ur.role_id
                          JOIN permissions p ON p.id = rp.permission_id
                          WHERE ur.user_id = u.id
                            AND p.name IN ('support:read', 'support:manage'))
                      AND EXISTS (
                          SELECT 1 FROM user_roles ur
                          JOIN role_permissions rp ON rp.role_id = ur.role_id
                          JOIN permissions p ON p.id = rp.permission_id
                          WHERE ur.user_id = u.id
                            AND p.name IN ('support:write', 'support:manage')))
                """, Boolean.class, userId));
    }

    // ------------------------------------------------------------------
    // Service: send_staff_message
    // ------------------------------------------------------------------

    /**
     * {@code send_staff_message}: write-gated reply with a conversation-wide
     * idempotency key that is actor-scoped for retries, auto-claim on first
     * reply (needs support:assign), then the post-commit hub publish + audit.
     */
    public SupportConversationDetail sendStaffMessage(long actorId, long conversationId,
            SupportMessageRequest request, String ipAddress, String userAgent) {
        com.hotelapp.core.security.PermissionGateHelper.check(actorId, "support:write");
        GuestSupport.requestIdIsValid(request == null ? null : request.clientMessageId());
        Long expectedVersion = request == null ? null : request.expectedVersion();
        if (expectedVersion == null) {
            throw ApiError.badRequest(
                    "A conversation version is required for support replies");
        }
        String body = GuestSupport.sanitizeRequiredMessage(
                request == null ? null : request.message());
        ConversationRow current = findMutationRow(conversationId);
        if (current == null) {
            throw ApiError.notFound("Support conversation not found");
        }
        String clientMessageId = request.clientMessageId();
        if (clientMessageId != null) {
            Boolean belongsToActor = staffClientMessageBelongsToActor(
                    conversationId, actorId, clientMessageId);
            if (Boolean.TRUE.equals(belongsToActor)) {
                // Retry replays the full detail — gate it behind support:read
                // like upstream so a write-only key cannot become a read.
                com.hotelapp.core.security.PermissionGateHelper.check(
                        actorId, "support:read");
                return staffDetail(conversationId);
            }
            if (Boolean.FALSE.equals(belongsToActor)) {
                throw ApiError.conflict(
                        "This message idempotency key belongs to another staff member");
            }
        }
        if ("closed".equals(current.status()) || "resolved".equals(current.status())) {
            throw ApiError.conflict(
                    "Reopen this conversation before sending another reply");
        }
        boolean isManager = canManage(actorId);
        if (current.assignedToUserId() != null) {
            ensureOwnerOrManager(current.assignedToUserId(), actorId, isManager);
        }
        if (expectedVersion != current.version()) {
            throw ApiError.conflict(
                    "This conversation changed. Refresh it before replying");
        }

        Mutation mutation = mutationFrom(current).withStatus("waiting_for_guest");
        if (mutation.assignedToUserId() == null) {
            com.hotelapp.core.security.PermissionGateHelper.check(actorId, "support:assign");
            mutation = new Mutation(mutation.status(), mutation.priority(),
                    mutation.assignedTeam(), actorId, mutation.escalationLevel(),
                    mutation.escalatedAt(), mutation.firstResponseDueAt(),
                    mutation.resolutionDueAt(), mutation.firstResponseAt(),
                    mutation.resolvedAt(), mutation.closedAt(), mutation.resolutionCode(),
                    mutation.resolutionSummary(), mutation.reopenCount(),
                    mutation.expectedVersion());
        }
        if (mutation.firstResponseAt() == null) {
            mutation = new Mutation(mutation.status(), mutation.priority(),
                    mutation.assignedTeam(), mutation.assignedToUserId(),
                    mutation.escalationLevel(), mutation.escalatedAt(),
                    mutation.firstResponseDueAt(), mutation.resolutionDueAt(),
                    OffsetDateTime.now(), mutation.resolvedAt(), mutation.closedAt(),
                    mutation.resolutionCode(), mutation.resolutionSummary(),
                    mutation.reopenCount(), mutation.expectedVersion());
        }
        mutation = mutation.withExpectedVersion(expectedVersion);

        if (!tx.applyMutation(conversationId, mutation, body, actorId, clientMessageId,
                "staff_replied", current.status(), null)) {
            throw ApiError.conflict(
                    "This conversation changed. Refresh it before replying");
        }
        hub.publish(SupportHub.ConversationChanged.of(guestIdOf(conversationId),
                conversationId));
        try {
            audit.event(actorId, "support.message_sent", "support_conversation",
                    conversationId, Map.of("author_type", "staff"), ipAddress, userAgent);
        } catch (Exception ignored) {
            // Post-commit audit is best-effort upstream (`let _ =`).
        }
        return staffDetail(conversationId);
    }

    private long guestIdOf(long conversationId) {
        Long guestId = jdbc.queryForObject(
                "SELECT guest_id FROM support_conversations WHERE id = ?",
                Long.class, conversationId);
        return guestId == null ? 0 : guestId;
    }

    private Boolean staffClientMessageBelongsToActor(long conversationId, long actorId,
            String clientMessageId) {
        List<Long> results = jdbc.query("""
                SELECT CASE WHEN author_user_id = ? THEN 1 ELSE 0 END
                FROM support_messages
                WHERE conversation_id = ? AND author_type = 'staff' AND client_message_id = ?
                LIMIT 1
                """, (rs, i) -> rs.getLong(1), actorId, conversationId, clientMessageId);
        return results.isEmpty() ? null : results.get(0) == 1;
    }

    private static Mutation mutationFrom(ConversationRow c) {
        return new Mutation(c.status(), c.priority(), c.queue(), c.assignedToUserId(),
                c.escalationLevel(), c.escalatedAt(), c.firstResponseDueAt(),
                c.resolutionDueAt(), c.firstResponseAt(), c.resolvedAt(), c.closedAt(),
                c.resolutionCode(), c.resolutionSummary(), c.reopenCount(), null);
    }

    // ------------------------------------------------------------------
    // Service: apply_staff_action
    // ------------------------------------------------------------------

    /**
     * {@code apply_staff_action}: the nine-action state machine with
     * actor-scoped idempotency, per-action permissions and version-guarded
     * mutation inside one transaction.
     */
    public SupportConversationDetail applyStaffAction(long actorId, long conversationId,
            SupportActionRequest input, String ipAddress, String userAgent) {
        String action = GuestSupport.validateAction(input == null ? null : input.action());
        requireActionPermission(actorId, action);
        GuestSupport.requestIdIsValid(input.clientActionId());
        ConversationRow current = findMutationRow(conversationId);
        if (current == null) {
            throw ApiError.notFound("Support conversation not found");
        }
        if (input.clientActionId() != null) {
            String storedAction = tx.findActionKeyAction(conversationId, actorId,
                    input.clientActionId());
            if (storedAction != null) {
                if (!storedAction.equals(action)) {
                    throw ApiError.conflict(
                            "This action idempotency key was already used for a different"
                                    + " action");
                }
                com.hotelapp.core.security.PermissionGateHelper.check(
                        actorId, "support:read");
                return staffDetail(conversationId);
            }
        }
        if (input.expectedVersion() == null) {
            throw ApiError.badRequest(
                    "A conversation version is required for support actions");
        }
        if (input.expectedVersion() != current.version()) {
            throw ApiError.conflict(
                    "This conversation changed. Refresh it before taking another action");
        }

        boolean isManager = canManage(actorId);
        String reason = GuestSupport.sanitizeOptionalReason(input.reason());
        Mutation mutation = mutationFrom(current)
                .withExpectedVersion(input.expectedVersion());
        String eventType;
        Map<String, Object> eventDetails = new LinkedHashMap<>();

        switch (action) {
            case "claim" -> {
                if (!isActiveStatus(current.status())) {
                    throw ApiError.conflict("Only active conversations can be claimed");
                }
                if (current.assignedToUserId() != null) {
                    if (current.assignedToUserId() == actorId) {
                        return staffDetail(conversationId);
                    }
                    throw ApiError.conflict(
                            "Another staff member already claimed this conversation");
                }
                mutation = mutation.withAssignee(actorId);
                eventType = "claimed";
            }
            case "assign" -> {
                if (!isActiveStatus(current.status())) {
                    throw ApiError.conflict("Only active conversations can be assigned");
                }
                if (input.assigneeId() != null && !isActiveSupportAgent(input.assigneeId())) {
                    throw ApiError.badRequest(
                            "Selected user is not an active support agent");
                }
                mutation = mutation.withAssignee(input.assigneeId());
                eventType = input.assigneeId() != null ? "assigned" : "returned_to_queue";
                eventDetails.put("assignee_id", input.assigneeId());
                eventDetails.put("reason", reason);
            }
            case "release" -> {
                if (!isActiveStatus(current.status())) {
                    throw ApiError.conflict(
                            "Only active conversations can be returned to the queue");
                }
                ensureOwnerOrManager(current.assignedToUserId(), actorId, isManager);
                mutation = mutation.withAssignee(null);
                eventType = "returned_to_queue";
                eventDetails.put("reason", reason);
            }
            case "set_priority" -> {
                if (!isActiveStatus(current.status())) {
                    throw ApiError.conflict(
                            "Only active conversations can be reprioritized");
                }
                if (input.priority() == null) {
                    throw ApiError.badRequest("A support priority is required");
                }
                String nextPriority = GuestSupport.validatePriority(input.priority());
                long[] previousSla = guestSupport.prioritySla(current.priority());
                long[] nextSla = guestSupport.prioritySla(nextPriority);
                mutation = mutation.withPriority(nextPriority);
                if (current.firstResponseAt() == null
                        && "waiting_for_staff".equals(current.status())) {
                    OffsetDateTime rebased = rebaseSlaDueAt(current.firstResponseDueAt(),
                            previousSla[0], nextSla[0]);
                    mutation = mutation.withFirstResponseDueAt(rebased != null ? rebased
                            : OffsetDateTime.now().plusMinutes(nextSla[0]));
                }
                if (current.resolvedAt() == null) {
                    OffsetDateTime rebased = rebaseSlaDueAt(current.resolutionDueAt(),
                            previousSla[1], nextSla[1]);
                    mutation = mutation.withResolutionDueAt(rebased != null ? rebased
                            : OffsetDateTime.now().plusMinutes(nextSla[1]));
                }
                eventType = "priority_changed";
                eventDetails.put("from", current.priority());
                eventDetails.put("to", nextPriority);
                eventDetails.put("reason", reason);
            }
            case "escalate" -> {
                if (reason == null) {
                    throw ApiError.badRequest("An escalation reason is required");
                }
                if (!isActiveStatus(current.status())) {
                    throw ApiError.conflict("Only active conversations can be escalated");
                }
                int nextLevel = Math.min(mutation.escalationLevel() + 1, 3);
                mutation = mutation.withEscalation(nextLevel, OffsetDateTime.now());
                eventType = "escalated";
                eventDetails.put("reason", reason);
                eventDetails.put("level", nextLevel);
            }
            case "resolve" -> {
                ensureOwnerOrManager(current.assignedToUserId(), actorId, isManager);
                if (!isActiveStatus(current.status())) {
                    throw ApiError.conflict("Only active conversations can be resolved");
                }
                String resolutionCode = GuestSupport.sanitizeResolutionCode(
                        input.resolutionCode());
                if (resolutionCode == null) {
                    throw ApiError.badRequest("A resolution code is required");
                }
                String resolutionSummary = GuestSupport.sanitizeOptionalReason(
                        input.resolutionSummary());
                if (resolutionSummary == null) {
                    throw ApiError.badRequest("A public resolution summary is required");
                }
                mutation = mutation.resolve(resolutionCode, resolutionSummary);
                eventType = "resolved";
                eventDetails.put("resolution_code", resolutionCode);
            }
            case "close" -> {
                if (!"resolved".equals(current.status())) {
                    throw ApiError.conflict(
                            "Resolve this conversation before closing it");
                }
                if (reason == null) {
                    throw ApiError.badRequest("A closure reason is required");
                }
                mutation = mutation.close();
                eventType = "closed";
                eventDetails.put("reason", reason);
            }
            case "reopen" -> {
                if (!"resolved".equals(current.status()) && !"closed".equals(current.status())) {
                    throw ApiError.conflict(
                            "Only resolved or closed conversations can be reopened");
                }
                long resolutionSla = guestSupport.prioritySla(current.priority())[1];
                mutation = mutation.reopen(
                        OffsetDateTime.now().plusMinutes(resolutionSla));
                eventType = "reopened";
                eventDetails.put("reason", reason);
            }
            case "add_internal_note" -> {
                if (!isActiveStatus(current.status())) {
                    throw ApiError.conflict(
                            "Only active conversations can receive internal notes");
                }
                ensureOwnerOrManager(current.assignedToUserId(), actorId, isManager);
                if (reason == null) {
                    throw ApiError.badRequest("An internal note is required");
                }
                eventType = "internal_note";
                eventDetails.put("body", reason);
            }
            default -> throw ApiError.badRequest("Unsupported support action");
        }

        if (!tx.applyAction(conversationId, mutation, actorId, eventType,
                current.status(), eventDetails.isEmpty() ? null : eventDetails,
                input.clientActionId(), action)) {
            throw ApiError.conflict(
                    "This conversation changed. Refresh it before taking another action");
        }
        hub.publish(SupportHub.ConversationChanged.of(guestIdOf(conversationId),
                conversationId));
        try {
            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("from_status", current.status());
            auditDetails.put("to_status", mutation.status());
            auditDetails.put("action", action);
            audit.event(actorId, "support." + eventType, "support_conversation",
                    conversationId, auditDetails, ipAddress, userAgent);
        } catch (Exception ignored) {
            // Post-commit audit is best-effort upstream (`let _ =`).
        }
        return staffDetail(conversationId);
    }
}
