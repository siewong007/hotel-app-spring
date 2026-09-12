package com.hotelapp.communications;

import com.hotelapp.communications.CommsModels.AudienceCount;
import com.hotelapp.communications.CommsModels.AudienceGuest;
import com.hotelapp.communications.CommsModels.EmailCampaign;
import com.hotelapp.communications.CommsModels.EmailDelivery;
import com.hotelapp.communications.CommsModels.EmailSuppression;
import com.hotelapp.communications.CommsModels.EmailTemplate;
import com.hotelapp.communications.CommsModels.PreArrivalBooking;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared persistence for the communications domain, mirroring
 * {@code modules/communications/repository.rs}: row mappers plus the queries
 * used by both the HTTP surface (Task 6) and the worker/scheduler (Task 7).
 */
@Component
public class CommunicationsRepo {

    static final String CAMPAIGN_COLUMNS = """
            id, name, campaign_type, topic, status, subject, body_html, body_text,
            template_id, promotion_id, scheduled_at, started_at, completed_at,
            cancelled_at, total_recipients, sent_count, failed_count, error,
            created_by, cancelled_by, created_at, updated_at
            """;

    static final String DELIVERY_COLUMNS = """
            id, campaign_id, kind, guest_id, topic, recipient_email, subject, body_html,
            body_text, voucher_id, status, attempts, max_attempts, next_attempt_at,
            lease_owner, lease_expires_at, provider_message_id, idempotency_key,
            last_error, sent_at, created_at, updated_at
            """;

    static final String TEMPLATE_COLUMNS = """
            id, code, name, subject, body_html, body_text, variables, is_active,
            created_at, updated_at
            """;

    static final String SUPPRESSION_COLUMNS = """
            id, email, reason, source, notes, created_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CommunicationsRepo(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Row mappers
    // ------------------------------------------------------------------

    public EmailCampaign campaignFromRow(ResultSet rs) throws SQLException {
        return new EmailCampaign(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("campaign_type"),
                rs.getString("topic"),
                rs.getString("status"),
                rs.getString("subject"),
                rs.getString("body_html"),
                rs.getString("body_text"),
                (Long) rs.getObject("template_id"),
                (Long) rs.getObject("promotion_id"),
                rs.getObject("scheduled_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                rs.getObject("cancelled_at", OffsetDateTime.class),
                rs.getInt("total_recipients"),
                rs.getInt("sent_count"),
                rs.getInt("failed_count"),
                rs.getString("error"),
                (Long) rs.getObject("created_by"),
                (Long) rs.getObject("cancelled_by"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    public EmailTemplate templateFromRow(ResultSet rs) throws SQLException {
        List<String> variables = List.of();
        String raw = rs.getString("variables");
        if (raw != null) {
            try {
                variables = objectMapper.readValue(raw,
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, String.class));
            } catch (Exception ignored) {
                variables = List.of();
            }
        }
        return new EmailTemplate(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("subject"),
                rs.getString("body_html"),
                rs.getString("body_text"),
                variables,
                rs.getBoolean("is_active"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    public EmailSuppression suppressionFromRow(ResultSet rs) throws SQLException {
        return new EmailSuppression(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("reason"),
                rs.getString("source"),
                rs.getString("notes"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    public EmailDelivery deliveryFromRow(ResultSet rs) throws SQLException {
        return new EmailDelivery(
                rs.getLong("id"),
                (Long) rs.getObject("campaign_id"),
                rs.getString("kind"),
                rs.getLong("guest_id"),
                rs.getString("topic"),
                rs.getString("recipient_email"),
                rs.getString("subject"),
                rs.getString("body_html"),
                rs.getString("body_text"),
                (Long) rs.getObject("voucher_id"),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getInt("max_attempts"),
                rs.getObject("next_attempt_at", OffsetDateTime.class),
                rs.getString("lease_owner"),
                rs.getObject("lease_expires_at", OffsetDateTime.class),
                rs.getString("provider_message_id"),
                rs.getString("idempotency_key"),
                rs.getString("last_error"),
                rs.getObject("sent_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private AudienceGuest audienceGuestFromRow(ResultSet rs) throws SQLException {
        return new AudienceGuest(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("first_name"),
                rs.getString("nick_name"));
    }

    private PreArrivalBooking preArrivalFromRow(ResultSet rs) throws SQLException {
        return new PreArrivalBooking(
                rs.getLong("id"),
                rs.getLong("guest_id"),
                rs.getString("booking_number"),
                rs.getString("guest_name"),
                rs.getString("guest_email"),
                rs.getObject("check_in_date", LocalDate.class),
                rs.getObject("check_out_date", LocalDate.class),
                rs.getString("room_number"),
                rs.getString("room_type_name"));
    }

    // ------------------------------------------------------------------
    // Campaigns + templates + suppressions (shared reads)
    // ------------------------------------------------------------------

    public Optional<EmailCampaign> getCampaign(long id) {
        return jdbc.query(
                        "SELECT " + CAMPAIGN_COLUMNS + " FROM email_campaigns WHERE id = ?",
                        (rs, i) -> campaignFromRow(rs), id)
                .stream().findFirst();
    }

    public Optional<EmailTemplate> getTemplate(long id) {
        return jdbc.query(
                        "SELECT " + TEMPLATE_COLUMNS + " FROM email_templates WHERE id = ?",
                        (rs, i) -> templateFromRow(rs), id)
                .stream().findFirst();
    }

    public Optional<String> promotionStatus(long promotionId) {
        return jdbc.query(
                        "SELECT status FROM promotions WHERE id = ?",
                        (rs, i) -> rs.getString(1), promotionId)
                .stream().findFirst();
    }

    public Optional<String> promotionName(long promotionId) {
        return jdbc.query(
                        "SELECT name FROM promotions WHERE id = ?",
                        (rs, i) -> rs.getString(1), promotionId)
                .stream().findFirst();
    }

    /** {@code is_email_suppressed}. */
    public boolean isEmailSuppressed(String email) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_suppressions WHERE email = LOWER(?)",
                Long.class, email);
        return count != null && count > 0;
    }

    /**
     * {@code hotel_local_date}: "today" in the hotel's configured timezone
     * (the Postgres session timezone, set per connection upstream).
     */
    public LocalDate hotelLocalDate() {
        String raw = jdbc.queryForObject(
                "SELECT to_char(LOCALTIMESTAMP, 'YYYY-MM-DD')", String.class);
        return LocalDate.parse(raw);
    }

    // ------------------------------------------------------------------
    // Audience + scheduler reads
    // ------------------------------------------------------------------

    /** {@code count_audience_for_topic}: server-side counts; never recipients. */
    public AudienceCount countAudienceForTopic(String topic) {
        return jdbc.query("""
                SELECT
                    (SELECT COUNT(*) FROM guests g
                     WHERE g.is_active IS TRUE
                       AND g.email IS NOT NULL AND length(trim(g.email)) > 0
                       AND EXISTS (SELECT 1 FROM notification_subscriptions ns
                                   WHERE ns.guest_id = g.id AND ns.channel = 'email'
                                     AND ns.topic = ? AND ns.subscribed IS TRUE)
                       AND NOT EXISTS (SELECT 1 FROM email_suppressions es
                                       WHERE es.email = LOWER(g.email))) AS eligible,
                    (SELECT COUNT(*) FROM guests g
                     WHERE g.is_active IS TRUE
                       AND (g.email IS NULL OR length(trim(g.email)) = 0)) AS excluded_no_email,
                    (SELECT COUNT(*) FROM guests g
                     WHERE g.is_active IS NOT TRUE) AS excluded_inactive,
                    (SELECT COUNT(*) FROM guests g
                     WHERE g.is_active IS TRUE
                       AND g.email IS NOT NULL AND length(trim(g.email)) > 0
                       AND NOT EXISTS (SELECT 1 FROM notification_subscriptions ns
                                       WHERE ns.guest_id = g.id AND ns.channel = 'email'
                                         AND ns.topic = ? AND ns.subscribed IS TRUE)) AS excluded_unsubscribed,
                    (SELECT COUNT(*) FROM guests g
                     WHERE g.is_active IS TRUE
                       AND g.email IS NOT NULL AND length(trim(g.email)) > 0
                       AND EXISTS (SELECT 1 FROM notification_subscriptions ns
                                   WHERE ns.guest_id = g.id AND ns.channel = 'email'
                                     AND ns.topic = ? AND ns.subscribed IS TRUE)
                       AND EXISTS (SELECT 1 FROM email_suppressions es
                                   WHERE es.email = LOWER(g.email))) AS excluded_suppressed
                """,
                rs -> rs.next() ? new AudienceCount(
                        rs.getLong("eligible"),
                        rs.getLong("excluded_no_email"),
                        rs.getLong("excluded_inactive"),
                        rs.getLong("excluded_unsubscribed"),
                        rs.getLong("excluded_suppressed")) : null,
                topic, topic, topic);
    }

    /** {@code due_scheduled_campaigns}. */
    public List<EmailCampaign> dueScheduledCampaigns() {
        return jdbc.query(
                "SELECT " + CAMPAIGN_COLUMNS + " FROM email_campaigns"
                        + " WHERE status = 'scheduled' AND scheduled_at <= CURRENT_TIMESTAMP"
                        + " ORDER BY scheduled_at",
                (rs, i) -> campaignFromRow(rs));
    }

    /** {@code mark_campaign_running}: scheduled → running, one winner. */
    public boolean markCampaignRunning(long id) {
        return jdbc.update("""
                UPDATE email_campaigns
                SET status = 'running', started_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'scheduled'
                """, id) > 0;
    }

    /** {@code refresh_campaign_total}. */
    public void refreshCampaignTotal(long id) {
        jdbc.update("""
                UPDATE email_campaigns
                SET total_recipients = (SELECT COUNT(*) FROM email_deliveries
                                        WHERE campaign_id = ?),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, id, id);
    }

    /** {@code complete_campaign_if_done}: running → completed once nothing in flight. */
    public boolean completeCampaignIfDone(long campaignId) {
        return jdbc.update("""
                UPDATE email_campaigns SET
                    status = 'completed', completed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'running'
                  AND NOT EXISTS (SELECT 1 FROM email_deliveries
                                  WHERE campaign_id = ? AND status IN ('queued', 'sending'))
                """, campaignId, campaignId) > 0;
    }

    /** {@code audience_batch}: eligible recipients with no delivery row yet. */
    public List<AudienceGuest> audienceBatch(String topic, long campaignId, int limit) {
        return jdbc.query("""
                SELECT g.id, g.email, g.first_name, g.nick_name FROM guests g
                WHERE g.is_active IS TRUE
                  AND g.email IS NOT NULL AND length(trim(g.email)) > 0
                  AND EXISTS (SELECT 1 FROM notification_subscriptions ns
                              WHERE ns.guest_id = g.id AND ns.channel = 'email'
                                AND ns.topic = ? AND ns.subscribed IS TRUE)
                  AND NOT EXISTS (SELECT 1 FROM email_suppressions es
                                  WHERE es.email = LOWER(g.email))
                  AND NOT EXISTS (SELECT 1 FROM email_deliveries d
                                  WHERE d.campaign_id = ? AND d.guest_id = g.id)
                ORDER BY g.id
                LIMIT ?
                """, (rs, i) -> audienceGuestFromRow(rs), topic, campaignId, limit);
    }

    /** {@code due_pre_arrival_bookings}: arrivals in-window not yet reminded. */
    public List<PreArrivalBooking> duePreArrivalBookings(LocalDate today, long windowDays) {
        return jdbc.query("""
                SELECT b.id,
                       g.id AS guest_id,
                       b.booking_number,
                       g.nick_name AS guest_name,
                       g.email AS guest_email,
                       b.check_in_date,
                       b.check_out_date,
                       r.room_number,
                       rt.name AS room_type_name
                FROM bookings b
                JOIN guests g ON g.id = b.guest_id
                LEFT JOIN rooms r ON r.id = b.room_id
                LEFT JOIN room_types rt ON rt.id = r.room_type_id
                WHERE b.status IN ('confirmed', 'pending')
                  AND b.check_in_date >= ?
                  AND b.check_in_date <= ?
                  AND COALESCE(g.email, '') <> ''
                  AND NOT EXISTS (
                      SELECT 1 FROM email_deliveries ed
                      WHERE ed.idempotency_key = 'pre-arrival:' || b.id::text
                  )
                ORDER BY b.check_in_date
                LIMIT 500
                """, (rs, i) -> preArrivalFromRow(rs), today, today.plusDays(windowDays));
    }

    /**
     * {@code birthday_targets}: guests whose (month, day) matches either pair,
     * eligible for the birthday topic, with no voucher yet for this year's
     * {@code sourceReference} nor for {@code promotionId}.
     */
    public List<AudienceGuest> birthdayTargets(
            int month1, int day1, int month2, int day2,
            String sourceReference, long promotionId, int limit) {
        return jdbc.query("""
                SELECT g.id, g.email, g.first_name, g.nick_name FROM guests g
                WHERE g.is_active IS TRUE
                  AND g.email IS NOT NULL AND length(trim(g.email)) > 0
                  AND g.date_of_birth IS NOT NULL
                  AND (
                      (EXTRACT(MONTH FROM g.date_of_birth) = ? AND EXTRACT(DAY FROM g.date_of_birth) = ?)
                      OR (EXTRACT(MONTH FROM g.date_of_birth) = ? AND EXTRACT(DAY FROM g.date_of_birth) = ?)
                  )
                  AND EXISTS (SELECT 1 FROM notification_subscriptions ns
                              WHERE ns.guest_id = g.id AND ns.channel = 'email'
                                AND ns.topic = 'birthday_voucher' AND ns.subscribed IS TRUE)
                  AND NOT EXISTS (SELECT 1 FROM email_suppressions es
                                  WHERE es.email = LOWER(g.email))
                  AND NOT EXISTS (SELECT 1 FROM vouchers v
                                  WHERE v.guest_id = g.id AND v.source_reference = ?)
                  AND NOT EXISTS (SELECT 1 FROM vouchers v
                                  WHERE v.guest_id = g.id AND v.promotion_id = ?)
                ORDER BY g.id
                LIMIT ?
                """, (rs, i) -> audienceGuestFromRow(rs),
                month1, day1, month2, day2, sourceReference, promotionId, limit);
    }

    // ------------------------------------------------------------------
    // Outbox worker
    // ------------------------------------------------------------------

    /**
     * {@code claim_due_deliveries}: atomically leases up to {@code batch} due
     * deliveries for this worker — queued rows whose retry time arrived plus
     * rows whose previous lease expired (crash recovery). Claiming increments
     * {@code attempts}.
     */
    public List<EmailDelivery> claimDueDeliveries(String workerId, int batch) {
        return jdbc.query(
                """
                UPDATE email_deliveries SET
                    status = 'sending',
                    lease_owner = ?,
                    lease_expires_at = CURRENT_TIMESTAMP + INTERVAL '5 minutes',
                    attempts = attempts + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id IN (
                    SELECT id FROM email_deliveries
                    WHERE (status = 'queued' AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (status = 'sending' AND lease_expires_at < CURRENT_TIMESTAMP)
                    ORDER BY next_attempt_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING
                """ + DELIVERY_COLUMNS,
                (rs, i) -> deliveryFromRow(rs), workerId, batch);
    }

    /** {@code is_guest_deliverable}: active + live per-topic subscription. */
    public boolean isGuestDeliverable(long guestId, String topic) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM guests g
                WHERE g.id = ? AND g.is_active IS TRUE
                  AND EXISTS (SELECT 1 FROM notification_subscriptions ns
                              WHERE ns.guest_id = g.id AND ns.channel = 'email'
                                AND ns.topic = ? AND ns.subscribed IS TRUE)
                """, Long.class, guestId, topic);
        return count != null && count > 0;
    }

    /** {@code is_guest_active}: existence + activity for transactional kinds. */
    public boolean isGuestActive(long guestId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM guests g WHERE g.id = ? AND g.is_active IS TRUE",
                Long.class, guestId);
        return count != null && count > 0;
    }
}
