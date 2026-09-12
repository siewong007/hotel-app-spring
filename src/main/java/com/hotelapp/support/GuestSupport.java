package com.hotelapp.support;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.settings.HotelSettings;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.support.GuestSupportTx.Mutation;
import com.hotelapp.support.GuestSupportTx.NewConversation;
import com.hotelapp.support.SupportModels.CreateGuestSupportConversationRequest;
import com.hotelapp.support.SupportModels.GuestSupportConversation;
import com.hotelapp.support.SupportModels.GuestSupportConversationDetail;
import com.hotelapp.support.SupportModels.GuestSupportConversationListResponse;
import com.hotelapp.support.SupportModels.GuestSupportMessage;
import com.hotelapp.support.SupportModels.GuestSupportMessageRequest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Port of the guest slice of {@code modules/support/service.rs} — guest
 * conversation list/detail/create/message/reopen. The staff queue lives in a
 * later task; this file only carries what the portal surface needs.
 */
@Component
public class GuestSupport {

    private static final int DEFAULT_REOPEN_WINDOW_DAYS = 7;
    private static final int MAX_MESSAGE_CHARS = 4_000;
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("booking", "stay", "billing", "loyalty", "technical", "other");

    private final JdbcTemplate jdbc;
    private final GuestSupportTx tx;
    private final SupportHub hub;
    private final HotelSettings settings;
    private final AuditWriter audit;
    private final ObjectMapper objectMapper;

    public GuestSupport(JdbcTemplate jdbc, GuestSupportTx tx, SupportHub hub,
            HotelSettings settings, AuditWriter audit, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.hub = hub;
        this.settings = settings;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Validation + settings helpers
    // ------------------------------------------------------------------

    static void requestIdIsValid(String value) {
        if (value != null && (value.trim().isEmpty() || value.codePointCount(0, value.length()) > 128)) {
            throw ApiError.badRequest("Invalid client request identifier");
        }
    }

    /** {@code CreateGuestSupportConversationRequest.client_request_id} is required upstream. */
    private static String requireClientRequestId(CreateGuestSupportConversationRequest request) {
        String value = request == null ? null : request.clientRequestId();
        if (value == null) {
            throw ApiError.badRequest("Missing client request identifier");
        }
        requestIdIsValid(value);
        return value;
    }

    static String subjectForCategory(String category) {
        StringBuilder subject = new StringBuilder();
        for (String word : category.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (subject.length() > 0) {
                subject.append(' ');
            }
            subject.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return subject + " support request";
    }

    static String conversationNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return "SUP-" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + "-" + suffix.substring(0, 8).toUpperCase();
    }

    private boolean supportEnabled() {
        String value = settings.getString("support_enabled", "true")
                .toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "true", "1", "yes", "on" -> true;
            default -> false;
        };
    }

    private List<String> enabledSupportCategories() {
        String raw = settings.getString("support_categories",
                "[\"booking\",\"stay\",\"billing\",\"loyalty\",\"technical\",\"other\"]");
        List<String> categories = new ArrayList<>();
        try {
            for (Object value : objectMapper.readValue(raw, List.class)) {
                if (value instanceof String s) {
                    try {
                        categories.add(validateCategory(s));
                    } catch (ApiError ignored) {
                        // Unparseable configured categories drop out like upstream.
                    }
                }
            }
        } catch (Exception ignored) {
            // Malformed JSON falls through to the defaults below.
        }
        categories = categories.stream().sorted().distinct().toList();
        return categories.isEmpty() ? DEFAULT_CATEGORIES : categories;
    }

    private String validateEnabledSupportCategory(String value) {
        String category = validateCategory(value);
        if (enabledSupportCategories().contains(category)) {
            return category;
        }
        throw ApiError.badRequest("This support category is currently unavailable");
    }

    static String normalizedChoice(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace(' ', '_').replace('-', '_');
    }

    static String validateCategory(String value) {
        String normalized = normalizedChoice(value);
        if (DEFAULT_CATEGORIES.contains(normalized)) {
            return normalized;
        }
        throw ApiError.badRequest("Unsupported support category");
    }

    static String sanitizeRequiredMessage(String value) {
        String sanitized = Sanitizer.sanitizeNotes(value == null ? "" : value).trim();
        if (sanitized.isEmpty()) {
            throw ApiError.badRequest("A support message is required");
        }
        if (sanitized.codePointCount(0, sanitized.length()) > MAX_MESSAGE_CHARS) {
            throw ApiError.badRequest(
                    "Support messages cannot exceed " + MAX_MESSAGE_CHARS + " characters");
        }
        return sanitized;
    }

    private int reopenWindowDays() {
        return settings.getPositiveInt("support_reopen_window_days", DEFAULT_REOPEN_WINDOW_DAYS);
    }

    /** {@code priority_sla}: (first_response_minutes, resolution_minutes). */
    private long[] prioritySla(String priority) {
        long[] defaults = switch (priority) {
            case "urgent" -> new long[] {5, 30};
            case "high" -> new long[] {15, 120};
            case "low" -> new long[] {240, 1440};
            default -> new long[] {60, 480};
        };
        long first = settings.getPositiveInt(
                "support_first_response_" + priority + "_minutes", (int) defaults[0]);
        long resolution = settings.getPositiveInt(
                "support_resolution_" + priority + "_minutes", (int) defaults[1]);
        return new long[] {first, resolution};
    }

    static boolean reopenAllowed(OffsetDateTime resolvedAt, long windowDays) {
        return resolvedAt != null
                && !resolvedAt.isBefore(OffsetDateTime.now().minusDays(windowDays));
    }

    // ------------------------------------------------------------------
    // Repository: reads
    // ------------------------------------------------------------------

    /** Internal row carrying every column the guest view + mutations need. */
    record ConversationRow(
            long id, String category, String status, String priority, String queue,
            Long bookingId, String subject, Long assignedToUserId, int escalationLevel,
            OffsetDateTime escalatedAt, OffsetDateTime firstResponseDueAt,
            OffsetDateTime resolutionDueAt, OffsetDateTime firstResponseAt,
            OffsetDateTime resolvedAt, OffsetDateTime closedAt, String resolutionCode,
            String resolutionSummary, int reopenCount,
            OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime lastActivityAt,
            long version) {
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

    private static final String CONVERSATION_COLS = """
            c.id, c.category, c.status, c.priority, c.assigned_team, c.booking_id, c.subject,
            c.assigned_to_user_id, c.escalation_level, c.escalated_at, c.first_response_due_at,
            c.resolution_due_at, c.first_response_at, c.resolved_at, c.closed_at,
            c.resolution_code, c.resolution_summary, c.reopen_count, c.created_at, c.updated_at,
            c.last_activity_at, c.version
            """;

    private ConversationRow findGuestConversation(long guestId, long conversationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + CONVERSATION_COLS + " FROM support_conversations c"
                        + " WHERE c.id = ? AND c.guest_id = ?",
                conversationId, guestId);
        return rows.isEmpty() ? null : conversationFromRow(rows.get(0));
    }

    private static GuestSupportConversation guestView(ConversationRow c, int windowDays) {
        return new GuestSupportConversation(
                c.id(), c.category(), c.status(), "Hotel support team", c.bookingId(),
                c.subject(), c.createdAt(), c.updatedAt(), c.lastActivityAt(),
                c.firstResponseAt(), c.resolvedAt(), c.closedAt(), c.resolutionSummary(),
                "resolved".equals(c.status()) && reopenAllowed(c.resolvedAt(), windowDays),
                c.version());
    }

    private List<GuestSupportMessage> listMessages(long conversationId) {
        return jdbc.query("""
                SELECT sm.id, sm.author_type, sm.body, sm.created_at
                FROM support_messages sm
                WHERE sm.conversation_id = ?
                ORDER BY sm.created_at ASC, sm.id ASC
                """, (rs, i) -> new GuestSupportMessage(
                rs.getLong("id"), rs.getString("author_type"), rs.getString("body"),
                rs.getObject("created_at", OffsetDateTime.class)), conversationId);
    }

    private boolean bookingBelongsToGuest(long bookingId, long guestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM bookings WHERE id = ? AND guest_id = ?)",
                Boolean.class, bookingId, guestId));
    }

    private boolean guestClientMessageExists(long conversationId, long guestId,
            String clientMessageId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM support_messages WHERE conversation_id = ?
                    AND author_type = 'guest' AND author_guest_id = ? AND client_message_id = ?)
                """, Boolean.class, conversationId, guestId, clientMessageId));
    }

    // ------------------------------------------------------------------
    // Service: guest flows
    // ------------------------------------------------------------------

    /** {@code list_guest_conversations}. */
    public GuestSupportConversationListResponse listGuestConversations(long guestId,
            Long page, Long pageSize) {
        long p = page == null ? 1 : Math.max(page, 1);
        long size = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        long offset = (p - 1) * size;
        int windowDays = reopenWindowDays();
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM support_conversations WHERE guest_id = ?",
                Long.class, guestId);
        List<GuestSupportConversation> items = jdbc.query("""
                SELECT id, category, status, assigned_team, booking_id, subject, created_at,
                    updated_at, last_activity_at, first_response_at, resolved_at, closed_at,
                    resolution_summary, version,
                    CASE WHEN status = 'resolved'
                              AND resolved_at >= CURRENT_TIMESTAMP - (? * INTERVAL '1 day')
                         THEN true ELSE false END AS can_reopen
                FROM support_conversations
                WHERE guest_id = ?
                ORDER BY last_activity_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (rs, i) -> new GuestSupportConversation(
                rs.getLong("id"), rs.getString("category"), rs.getString("status"),
                "Hotel support team",
                rs.getObject("booking_id") instanceof Number n ? n.longValue() : null,
                rs.getString("subject"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("last_activity_at", OffsetDateTime.class),
                rs.getObject("first_response_at", OffsetDateTime.class),
                rs.getObject("resolved_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class),
                rs.getString("resolution_summary"),
                rs.getBoolean("can_reopen"),
                rs.getLong("version")), windowDays, guestId, size, offset);
        return new GuestSupportConversationListResponse(items, enabledSupportCategories(),
                supportEnabled(), total, p, size);
    }

    /** {@code get_guest_conversation}. */
    public GuestSupportConversationDetail getGuestConversation(long guestId, long conversationId) {
        ConversationRow conversation = findGuestConversation(guestId, conversationId);
        if (conversation == null) {
            throw ApiError.notFound("Support conversation not found");
        }
        return new GuestSupportConversationDetail(
                guestView(conversation, reopenWindowDays()), listMessages(conversationId));
    }

    /** {@code create_guest_conversation}. */
    public GuestSupportConversationDetail createGuestConversation(long guestId,
            CreateGuestSupportConversationRequest request, String ipAddress, String userAgent) {
        String clientRequestId = requireClientRequestId(request);
        Long existingId = tx.findGuestRequestConversation(guestId, clientRequestId);
        if (existingId != null) {
            return getGuestConversation(guestId, existingId);
        }
        if (!supportEnabled()) {
            throw ApiError.forbidden("Guest support is currently unavailable");
        }
        String category = validateEnabledSupportCategory(request.category());
        String message = sanitizeRequiredMessage(request.message());
        if (request.bookingId() != null
                && !bookingBelongsToGuest(request.bookingId(), guestId)) {
            throw ApiError.notFound("Booking not found");
        }
        long[] sla = prioritySla("normal");
        OffsetDateTime now = OffsetDateTime.now();
        NewConversation conversation = new NewConversation(
                conversationNumber(), guestId, request.bookingId(),
                subjectForCategory(category), category, "normal",
                now.plusMinutes(sla[0]), now.plusMinutes(sla[1]));

        long conversationId;
        try {
            conversationId = tx.createConversation(conversation, guestId,
                    clientRequestId, message);
        } catch (GuestSupportTx.DuplicateRequest duplicate) {
            Long existing = tx.findGuestRequestConversation(guestId, clientRequestId);
            if (existing == null) {
                throw ApiError.conflict("This support request is already being processed");
            }
            return getGuestConversation(guestId, existing);
        }
        hub.publish(new SupportHub.ConversationChanged(guestId, conversationId));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("guest_id", guestId);
        details.put("category", category);
        audit.event(null, "guest_portal.support_conversation_created", "support_conversation",
                conversationId, details, ipAddress, userAgent);
        return getGuestConversation(guestId, conversationId);
    }

    /** {@code send_guest_message}. */
    public GuestSupportConversationDetail sendGuestMessage(long guestId, long conversationId,
            GuestSupportMessageRequest request, String ipAddress, String userAgent) {
        requestIdIsValid(request == null ? null : request.clientMessageId());
        if (request.expectedVersion() == null) {
            throw ApiError.badRequest(
                    "A conversation version is required for support messages");
        }
        String body = sanitizeRequiredMessage(request.message());
        ConversationRow current = findGuestConversation(guestId, conversationId);
        if (current == null) {
            throw ApiError.notFound("Support conversation not found");
        }
        if (request.clientMessageId() != null
                && guestClientMessageExists(conversationId, guestId, request.clientMessageId())) {
            return getGuestConversation(guestId, conversationId);
        }
        if ("closed".equals(current.status())) {
            throw ApiError.conflict(
                    "This conversation is closed. Please start a new conversation");
        }
        if (request.expectedVersion() != current.version()) {
            throw ApiError.conflict(
                    "This conversation changed. Refresh it before sending another message");
        }

        Mutation mutation = mutationFrom(current);
        mutation = mutation.withExpectedVersion(request.expectedVersion());
        String eventType = "guest_replied";
        String fromStatus = current.status();
        if ("resolved".equals(current.status())) {
            if (!reopenAllowed(current.resolvedAt(), reopenWindowDays())) {
                throw ApiError.conflict(
                        "This resolved conversation can no longer be reopened");
            }
            long resolutionSla = prioritySla(current.priority())[1];
            mutation = mutation.reopen(
                    OffsetDateTime.now().plusMinutes(resolutionSla));
            eventType = "reopened_by_guest";
        } else {
            mutation = mutation.withStatus("waiting_for_staff");
            if ("waiting_for_guest".equals(current.status())
                    && current.resolutionDueAt() != null) {
                Duration pausedFor = Duration.between(current.lastActivityAt(), OffsetDateTime.now());
                if (!pausedFor.isNegative()) {
                    mutation = mutation.withResolutionDueAt(
                            current.resolutionDueAt().plus(pausedFor));
                }
            }
        }

        if (!tx.mutateConversation(conversationId, mutation, guestId, body,
                request.clientMessageId(), eventType, fromStatus, null)) {
            throw ApiError.conflict(
                    "This conversation changed. Refresh it before sending another message");
        }
        hub.publish(new SupportHub.ConversationChanged(guestId, conversationId));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("guest_id", guestId);
        details.put("event", eventType);
        audit.event(null, "guest_portal.support_message_sent", "support_conversation",
                conversationId, details, ipAddress, userAgent);
        return getGuestConversation(guestId, conversationId);
    }

    /** {@code reopen_guest_conversation}. */
    public GuestSupportConversationDetail reopenGuestConversation(long guestId,
            long conversationId, String ipAddress, String userAgent) {
        ConversationRow current = findGuestConversation(guestId, conversationId);
        if (current == null) {
            throw ApiError.notFound("Support conversation not found");
        }
        if ("waiting_for_staff".equals(current.status())) {
            return getGuestConversation(guestId, conversationId);
        }
        if (!"resolved".equals(current.status())
                || !reopenAllowed(current.resolvedAt(), reopenWindowDays())) {
            throw ApiError.conflict(
                    "This conversation can no longer be reopened. Please start a new one");
        }
        long resolutionSla = prioritySla(current.priority())[1];
        Mutation mutation = mutationFrom(current)
                .reopen(OffsetDateTime.now().plusMinutes(resolutionSla))
                .withExpectedVersion(current.version());

        if (!tx.mutateConversation(conversationId, mutation, guestId, null, null,
                "reopened_by_guest", "resolved", null)) {
            throw ApiError.conflict(
                    "This conversation changed. Refresh it before reopening it");
        }
        hub.publish(new SupportHub.ConversationChanged(guestId, conversationId));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("guest_id", guestId);
        audit.event(null, "guest_portal.support_reopened", "support_conversation",
                conversationId, details, ipAddress, userAgent);
        return getGuestConversation(guestId, conversationId);
    }

    private static Mutation mutationFrom(ConversationRow c) {
        return new Mutation(c.status(), c.priority(), c.queue(), c.assignedToUserId(),
                c.escalationLevel(), c.escalatedAt(), c.firstResponseDueAt(),
                c.resolutionDueAt(), c.firstResponseAt(), c.resolvedAt(), c.closedAt(),
                c.resolutionCode(), c.resolutionSummary(), c.reopenCount(), null);
    }
}
