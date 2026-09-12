package com.hotelapp.communications;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the guest-facing slice of {@code modules/communications}:
 * notification-preference reads/writes and the signup marketing-consent
 * recorder. All writes land in notification_subscriptions +
 * notification_consent_events plus an audit row, transactionally.
 */
@Component
public class GuestComms {

    public static final String CHANNEL_EMAIL = "email";
    public static final List<String> TOPICS =
            List.of("announcement", "promotion", "birthday_voucher");

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;

    public GuestComms(JdbcTemplate jdbc, AuditWriter audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ------------------------------------------------------------------
    // Models (mirrors modules/communications/models.rs)
    // ------------------------------------------------------------------

    public record SubscriptionUpdateInput(String topic, boolean subscribed) {
    }

    public record PreferenceUpdateInput(
            List<SubscriptionUpdateInput> subscriptions,
            @JsonProperty("policy_version") String policyVersion) {
    }

    public record TopicPreference(String topic, boolean subscribed) {
    }

    public record PreferencesResponse(List<TopicPreference> subscriptions) {
    }

    /** {@code validate_topic}: normalize, then require a known topic. */
    public static String validateTopic(String topic) {
        String normalized = topic == null ? "" : topic.trim().toLowerCase();
        if (TOPICS.contains(normalized)) {
            return normalized;
        }
        throw ApiError.badRequest("Unsupported notification topic");
    }

    private String getGuestEmail(long guestId) {
        List<String> rows = jdbc.query(
                "SELECT email FROM guests WHERE id = ?",
                (rs, i) -> rs.getString(1), guestId);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Guest not found");
        }
        return rows.get(0);
    }

    private List<TopicPreference> topicPreferences(long guestId) {
        Map<String, Boolean> emailSubs = new LinkedHashMap<>();
        jdbc.query(
                "SELECT topic, subscribed FROM notification_subscriptions "
                        + "WHERE guest_id = ? AND channel = 'email' ORDER BY topic",
                rs -> {
                    emailSubs.put(rs.getString("topic"), rs.getBoolean("subscribed"));
                },
                guestId);
        List<TopicPreference> result = new ArrayList<>();
        for (String topic : TOPICS) {
            result.add(new TopicPreference(topic, emailSubs.getOrDefault(topic, false)));
        }
        return result;
    }

    /** GET /guest-portal/me/notification-preferences. */
    public PreferencesResponse getPreferences(long guestId) {
        getGuestEmail(guestId);
        return new PreferencesResponse(topicPreferences(guestId));
    }

    /** {@code get_guest_email}: Some(email) or NotFound when the guest is gone. */
    public String guestEmail(long guestId) {
        return getGuestEmail(guestId);
    }

    /** {@code list_consent_events_for_guest}: newest-first consent history. */
    public List<CommsModels.ConsentEvent> listConsentEvents(long guestId, int limit) {
        return jdbc.query("""
                SELECT id, guest_id, channel, topic, action, source, policy_version,
                       actor_type, actor_user_id, created_at
                FROM notification_consent_events
                WHERE guest_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """,
                (rs, i) -> new CommsModels.ConsentEvent(
                        rs.getLong("id"),
                        rs.getLong("guest_id"),
                        rs.getString("channel"),
                        rs.getString("topic"),
                        rs.getString("action"),
                        rs.getString("source"),
                        rs.getString("policy_version"),
                        rs.getString("actor_type"),
                        (Long) rs.getObject("actor_user_id"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class)),
                guestId, limit);
    }

    /** {@code guest_consent_status}: preferences plus the last 50 consent events. */
    public CommsModels.ConsentStatusResponse guestConsentStatus(long guestId) {
        getGuestEmail(guestId);
        return new CommsModels.ConsentStatusResponse(
                topicPreferences(guestId), listConsentEvents(guestId, 50));
    }

    private record ChangeRequest(
            long guestId,
            List<SubscriptionUpdateInput> changes,
            String source,
            String policyVersion,
            String actorType,
            Long actorUserId,
            String ipAddress,
            String userAgent) {
    }

    /**
     * {@code apply_preference_changes}: one batch of subscription changes plus
     * its consent provenance. Runs inside the caller's transaction — the public
     * entry points below carry the boundary (a self-invoked {@code @Transactional}
     * would never fire).
     */
    private void applyPreferenceChanges(ChangeRequest request) {
        for (SubscriptionUpdateInput change : request.changes()) {
            jdbc.update("""
                    INSERT INTO notification_subscriptions
                        (guest_id, channel, topic, subscribed, source, policy_version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (guest_id, channel, topic) DO UPDATE SET
                        subscribed = EXCLUDED.subscribed,
                        source = EXCLUDED.source,
                        policy_version = EXCLUDED.policy_version,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    request.guestId(), CHANNEL_EMAIL, change.topic(), change.subscribed(),
                    request.source(), request.policyVersion());
            jdbc.update("""
                    INSERT INTO notification_consent_events
                        (guest_id, channel, topic, action, source, policy_version,
                         actor_type, actor_user_id, ip_address, user_agent)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.guestId(), CHANNEL_EMAIL, change.topic(),
                    change.subscribed() ? "opt_in" : "opt_out", request.source(),
                    request.policyVersion(), request.actorType(), request.actorUserId(),
                    request.ipAddress(), request.userAgent());
        }
        List<Map<String, Object>> changeDetails = request.changes().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<String, Object>();
                    m.put("topic", c.topic());
                    m.put("subscribed", c.subscribed());
                    return m;
                })
                .toList();
        audit.event(request.actorUserId(), "subscription.updated", "guest", request.guestId(),
                Map.of("source", request.source(), "changes", changeDetails));
    }

    private static List<SubscriptionUpdateInput> validateChanges(PreferenceUpdateInput input) {
        if (input == null || input.subscriptions() == null || input.subscriptions().isEmpty()) {
            throw ApiError.badRequest("No subscription changes provided");
        }
        return input.subscriptions().stream()
                .map(change -> new SubscriptionUpdateInput(
                        validateTopic(change.topic()), change.subscribed()))
                .toList();
    }

    /** PUT /guest-portal/me/notification-preferences. */
    @Transactional
    public PreferencesResponse updateMyPreferences(long guestId, PreferenceUpdateInput input,
            String ipAddress, String userAgent) {
        List<SubscriptionUpdateInput> changes = validateChanges(input);
        getGuestEmail(guestId);
        applyPreferenceChanges(new ChangeRequest(
                guestId, changes, "guest_portal",
                input.policyVersion(), "guest", null, ipAddress, userAgent));
        return getPreferences(guestId);
    }

    /**
     * {@code record_signup_marketing_consent}: the marketing decision from a
     * signup or booking form — an explicit opt-in/opt-out on every topic so a
     * refusal is distinguishable from never having been asked.
     */
    @Transactional
    public void recordSignupMarketingConsent(long guestId, boolean optedIn, String source,
            String policyVersion, String ipAddress, String userAgent) {
        List<SubscriptionUpdateInput> changes = TOPICS.stream()
                .map(topic -> new SubscriptionUpdateInput(topic, optedIn))
                .toList();
        applyPreferenceChanges(new ChangeRequest(
                guestId, changes, source, policyVersion, "guest", null, ipAddress, userAgent));
    }

    /** {@code record_staff_consent}: staff-recorded opt-in/out on behalf of a guest. */
    @Transactional
    public CommsModels.ConsentStatusResponse recordStaffConsent(long actorId, long guestId,
            PreferenceUpdateInput input, String ipAddress, String userAgent) {
        List<SubscriptionUpdateInput> changes = validateChanges(input);
        getGuestEmail(guestId);
        applyPreferenceChanges(new ChangeRequest(
                guestId, changes, "staff", input.policyVersion(), "staff", actorId,
                ipAddress, userAgent));
        return new CommsModels.ConsentStatusResponse(
                topicPreferences(guestId), listConsentEvents(guestId, 50));
    }

    /**
     * The unsubscribe flow's first transaction: validated subscription changes
     * with {@code unsubscribe_link} provenance, mirroring upstream's separate
     * {@code pool.begin()} before the suppression write.
     */
    @Transactional
    public void applyUnsubscribeChanges(long guestId, List<SubscriptionUpdateInput> changes,
            String ipAddress, String userAgent) {
        applyPreferenceChanges(new ChangeRequest(
                guestId, changes, "unsubscribe_link", null, "guest", null,
                ipAddress, userAgent));
    }

    /**
     * The unsubscribe flow's second transaction: a global-unsubscribe email
     * suppression row plus the {@code consent.opt_out} audit event.
     */
    @Transactional
    public void recordGlobalUnsubscribe(long guestId, CommsValidation.SuppressionDraft draft,
            String ipAddress, String userAgent) {
        jdbc.update("""
                INSERT INTO email_suppressions (email, reason, source, notes)
                VALUES (LOWER(?), ?, ?, ?)
                ON CONFLICT (email) DO UPDATE SET
                    reason = EXCLUDED.reason,
                    source = EXCLUDED.source,
                    notes = EXCLUDED.notes
                """, draft.email(), draft.reason(), "unsubscribe_link", draft.notes());
        audit.event(null, "consent.opt_out", "guest", guestId,
                Map.of("global", true), ipAddress, userAgent);
    }
}
