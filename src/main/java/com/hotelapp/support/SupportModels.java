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
}
