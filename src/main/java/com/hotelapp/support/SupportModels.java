package com.hotelapp.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs mirroring {@code modules/support/models.rs} — the guest-facing
 * conversation and message shapes. Staff identity fields never cross the
 * guest boundary (see {@code GuestSupportMessage}).
 */
public final class SupportModels {

    private SupportModels() {
    }

    public record GuestSupportMessage(
            long id,
            @JsonProperty("author_type") String authorType,
            String body,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    public record GuestSupportConversation(
            long id,
            String category,
            String status,
            @JsonProperty("assigned_team") String assignedTeam,
            @JsonProperty("booking_id") Long bookingId,
            String subject,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt,
            @JsonProperty("last_activity_at") OffsetDateTime lastActivityAt,
            @JsonProperty("first_response_at") OffsetDateTime firstResponseAt,
            @JsonProperty("resolved_at") OffsetDateTime resolvedAt,
            @JsonProperty("closed_at") OffsetDateTime closedAt,
            @JsonProperty("resolution_summary") String resolutionSummary,
            @JsonProperty("can_reopen") boolean canReopen,
            long version) {
    }

    public record GuestSupportConversationDetail(
            GuestSupportConversation conversation,
            List<GuestSupportMessage> messages) {
    }

    public record GuestSupportConversationListResponse(
            List<GuestSupportConversation> items,
            List<String> categories,
            boolean enabled,
            long total,
            long page,
            @JsonProperty("page_size") long pageSize) {
    }

    public record CreateGuestSupportConversationRequest(
            String category,
            String message,
            @JsonProperty("booking_id") Long bookingId,
            @JsonProperty("client_request_id") String clientRequestId) {
    }

    public record GuestSupportMessageRequest(
            String message,
            @JsonProperty("client_message_id") String clientMessageId,
            @JsonProperty("expected_version") Long expectedVersion) {
    }

    // ------------------------------------------------------------------
    // Staff surface
    // ------------------------------------------------------------------

    /**
     * {@code SupportConversationSummary} + the flattened {@code
     * SupportConversation} fields — upstream serializes the summary inline,
     * so this record already carries the flat wire shape.
     */
    public record StaffConversation(
            long id,
            @JsonProperty("conversation_number") String conversationNumber,
            @JsonProperty("guest_id") long guestId,
            @JsonProperty("guest_name") String guestName,
            @JsonProperty("guest_email") String guestEmail,
            @JsonProperty("booking_id") Long bookingId,
            @JsonProperty("booking_reference") String bookingReference,
            @JsonProperty("room_number") String roomNumber,
            String category,
            String status,
            String priority,
            String queue,
            @JsonProperty("assigned_to_user_id") Long assignedToUserId,
            @JsonProperty("assigned_to_name") String assignedToName,
            @JsonProperty("escalation_level") int escalationLevel,
            @JsonProperty("escalated_at") OffsetDateTime escalatedAt,
            @JsonProperty("first_response_due_at") OffsetDateTime firstResponseDueAt,
            @JsonProperty("resolution_due_at") OffsetDateTime resolutionDueAt,
            @JsonProperty("first_response_at") OffsetDateTime firstResponseAt,
            @JsonProperty("resolved_at") OffsetDateTime resolvedAt,
            @JsonProperty("closed_at") OffsetDateTime closedAt,
            @JsonProperty("last_message_preview") String lastMessagePreview,
            @JsonProperty("last_message_at") OffsetDateTime lastMessageAt,
            @JsonProperty("last_activity_at") OffsetDateTime lastActivityAt,
            @JsonProperty("unread_count") long unreadCount,
            @JsonProperty("is_sla_at_risk") boolean slaAtRisk,
            @JsonProperty("is_sla_breached") boolean slaBreached,
            long version,
            String subject,
            @JsonProperty("stay_status") String stayStatus,
            @JsonProperty("check_in_date") java.time.LocalDate checkInDate,
            @JsonProperty("check_out_date") java.time.LocalDate checkOutDate,
            @JsonProperty("resolution_code") String resolutionCode,
            @JsonProperty("resolution_summary") String resolutionSummary,
            @JsonProperty("reopen_count") int reopenCount,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt) {
    }

    /** {@code SupportMessage} — the staff shape keeps author identity. */
    public record StaffMessage(
            long id,
            @JsonProperty("conversation_id") long conversationId,
            @JsonProperty("author_type") String authorType,
            @JsonProperty("author_user_id") Long authorUserId,
            @JsonProperty("author_guest_id") Long authorGuestId,
            @JsonProperty("author_name") String authorName,
            String body,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    /** {@code SupportEvent} — {@code body} surfaces {@code details.body}. */
    public record StaffEvent(
            long id,
            @JsonProperty("conversation_id") long conversationId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("actor_user_id") Long actorUserId,
            @JsonProperty("actor_name") String actorName,
            String body,
            Object metadata,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    public record SupportConversationDetail(
            StaffConversation conversation,
            List<StaffMessage> messages,
            List<StaffEvent> events) {
    }

    public record SupportQueueMetrics(
            @JsonProperty("total_open") long totalOpen,
            long unassigned,
            @JsonProperty("waiting_for_staff") long waitingForStaff,
            @JsonProperty("waiting_for_guest") long waitingForGuest,
            @JsonProperty("at_risk") long atRisk,
            long breached) {
    }

    public record SupportConversationListResponse(
            List<StaffConversation> items,
            long total,
            long page,
            @JsonProperty("page_size") long pageSize,
            SupportQueueMetrics metrics) {
    }

    public record SupportAgent(
            long id,
            String name,
            String email,
            @JsonProperty("is_available") boolean available) {
    }

    public record SupportMessageRequest(
            String message,
            @JsonProperty("client_message_id") String clientMessageId,
            @JsonProperty("expected_version") Long expectedVersion) {
    }

    public record SupportActionRequest(
            String action,
            @JsonProperty("expected_version") Long expectedVersion,
            @JsonProperty("assignee_id") Long assigneeId,
            String priority,
            String reason,
            @JsonProperty("resolution_code") String resolutionCode,
            @JsonProperty("resolution_summary") String resolutionSummary,
            @JsonProperty("client_action_id") String clientActionId) {
    }
}
