package com.hotelapp.communications;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs mirroring {@code modules/communications/models.rs}. Field names map
 * one-to-one to the upstream serde output — including null placeholders, which
 * upstream serializes rather than skips.
 */
public final class CommsModels {

    private CommsModels() {
    }

    // ------------------------------------------------------------------
    // Row-shaped responses
    // ------------------------------------------------------------------

    public record NotificationSubscription(
            long id,
            @JsonProperty("guest_id") long guestId,
            String channel,
            String topic,
            boolean subscribed,
            String source,
            @JsonProperty("policy_version") String policyVersion,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt) {
    }

    public record ConsentEvent(
            long id,
            @JsonProperty("guest_id") long guestId,
            String channel,
            String topic,
            String action,
            String source,
            @JsonProperty("policy_version") String policyVersion,
            @JsonProperty("actor_type") String actorType,
            @JsonProperty("actor_user_id") Long actorUserId,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    public record EmailCampaign(
            long id,
            String name,
            @JsonProperty("campaign_type") String campaignType,
            String topic,
            String status,
            String subject,
            @JsonProperty("body_html") String bodyHtml,
            @JsonProperty("body_text") String bodyText,
            @JsonProperty("template_id") Long templateId,
            @JsonProperty("promotion_id") Long promotionId,
            @JsonProperty("scheduled_at") OffsetDateTime scheduledAt,
            @JsonProperty("started_at") OffsetDateTime startedAt,
            @JsonProperty("completed_at") OffsetDateTime completedAt,
            @JsonProperty("cancelled_at") OffsetDateTime cancelledAt,
            @JsonProperty("total_recipients") int totalRecipients,
            @JsonProperty("sent_count") int sentCount,
            @JsonProperty("failed_count") int failedCount,
            String error,
            @JsonProperty("created_by") Long createdBy,
            @JsonProperty("cancelled_by") Long cancelledBy,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt) {
    }

    /**
     * {@code DeliverySummary}: the masked staff-facing delivery row. Raw
     * recipient addresses and rendered bodies never leave the server.
     */
    public record DeliverySummary(
            long id,
            @JsonProperty("campaign_id") Long campaignId,
            String kind,
            @JsonProperty("guest_id") long guestId,
            String topic,
            String subject,
            @JsonProperty("recipient_masked") String recipientMasked,
            String status,
            int attempts,
            @JsonProperty("last_error") String lastError,
            @JsonProperty("sent_at") OffsetDateTime sentAt,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    /**
     * {@code DeliveryFeedItem}: the summary fields plus the derived priority
     * tier (upstream serde-flattens the summary).
     */
    public record DeliveryFeedItem(
            long id,
            @JsonProperty("campaign_id") Long campaignId,
            String kind,
            @JsonProperty("guest_id") long guestId,
            String topic,
            String subject,
            @JsonProperty("recipient_masked") String recipientMasked,
            String status,
            int attempts,
            @JsonProperty("last_error") String lastError,
            @JsonProperty("sent_at") OffsetDateTime sentAt,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            String tier) {
    }

    public record EmailSuppression(
            long id,
            String email,
            String reason,
            String source,
            String notes,
            @JsonProperty("created_at") OffsetDateTime createdAt) {
    }

    public record EmailTemplate(
            long id,
            String code,
            String name,
            String subject,
            @JsonProperty("body_html") String bodyHtml,
            @JsonProperty("body_text") String bodyText,
            List<String> variables,
            @JsonProperty("is_active") boolean isActive,
            @JsonProperty("created_at") OffsetDateTime createdAt,
            @JsonProperty("updated_at") OffsetDateTime updatedAt) {
    }

    public record AudienceCount(
            long eligible,
            @JsonProperty("excluded_no_email") long excludedNoEmail,
            @JsonProperty("excluded_inactive") long excludedInactive,
            @JsonProperty("excluded_unsubscribed") long excludedUnsubscribed,
            @JsonProperty("excluded_suppressed") long excludedSuppressed) {
    }

    // ------------------------------------------------------------------
    // List envelopes + previews
    // ------------------------------------------------------------------

    public record CampaignListResponse(
            List<EmailCampaign> items, long total, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    public record DeliveryFeedResponse(
            List<DeliveryFeedItem> items, long total, long unread, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    public record DeliveryListResponse(
            List<DeliverySummary> items, long total, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    public record SuppressionListResponse(
            List<EmailSuppression> items, long total, long page,
            @JsonProperty("page_size") long pageSize) {
    }

    public record PreviewResponse(
            String subject,
            @JsonProperty("body_html") String bodyHtml,
            AudienceCount audience) {
    }

    public record ConsentStatusResponse(
            List<GuestComms.TopicPreference> subscriptions,
            List<ConsentEvent> events) {
    }

    // ------------------------------------------------------------------
    // Inputs
    // ------------------------------------------------------------------

    public record CampaignInput(
            String name,
            @JsonProperty("campaign_type") String campaignType,
            String subject,
            @JsonProperty("body_html") String bodyHtml,
            @JsonProperty("body_text") String bodyText,
            @JsonProperty("template_id") Long templateId,
            @JsonProperty("promotion_id") Long promotionId) {
    }

    public record TemplateInput(
            String code,
            String name,
            String subject,
            @JsonProperty("body_html") String bodyHtml,
            @JsonProperty("body_text") String bodyText,
            List<String> variables,
            @JsonProperty("is_active") Boolean isActive) {
    }

    public record TestSendInput(@JsonProperty("recipient_email") String recipientEmail) {
    }

    public record SuppressionInput(String email, String reason, String notes) {
    }

    public record ScheduleCampaignInput(
            @JsonProperty("scheduled_at") OffsetDateTime scheduledAt) {
    }

    public record UnsubscribeApplyInput(String topic, Boolean global) {
    }
}
