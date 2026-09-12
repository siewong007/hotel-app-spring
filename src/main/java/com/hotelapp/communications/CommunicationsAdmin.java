package com.hotelapp.communications;

import tools.jackson.databind.ObjectMapper;
import com.hotelapp.communications.CommsModels.AudienceCount;
import com.hotelapp.communications.CommsModels.CampaignInput;
import com.hotelapp.communications.CommsModels.CampaignListResponse;
import com.hotelapp.communications.CommsModels.ConsentStatusResponse;
import com.hotelapp.communications.CommsModels.DeliveryFeedItem;
import com.hotelapp.communications.CommsModels.DeliveryFeedResponse;
import com.hotelapp.communications.CommsModels.DeliveryListResponse;
import com.hotelapp.communications.CommsModels.DeliverySummary;
import com.hotelapp.communications.CommsModels.EmailCampaign;
import com.hotelapp.communications.CommsModels.EmailSuppression;
import com.hotelapp.communications.CommsModels.EmailTemplate;
import com.hotelapp.communications.CommsModels.PreviewResponse;
import com.hotelapp.communications.CommsModels.ScheduleCampaignInput;
import com.hotelapp.communications.CommsModels.SuppressionInput;
import com.hotelapp.communications.CommsModels.SuppressionListResponse;
import com.hotelapp.communications.CommsModels.TemplateInput;
import com.hotelapp.communications.CommsModels.TestSendInput;
import com.hotelapp.communications.CommsModels.UnsubscribeApplyInput;
import com.hotelapp.communications.CommsValidation.CampaignDraft;
import com.hotelapp.communications.CommsValidation.SuppressionDraft;
import com.hotelapp.communications.CommsValidation.TemplateDraft;
import com.hotelapp.communications.GuestComms.PreferencesResponse;
import com.hotelapp.communications.GuestComms.SubscriptionUpdateInput;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the staff-facing and public-unsubscribe slices of
 * {@code modules/communications/service.rs}.
 *
 * <p>Safety invariants mirrored from upstream: audit details never contain
 * email bodies, recipient addresses, or voucher codes — only ids, topics,
 * statuses, and counts. Bulk sending never happens in an HTTP request:
 * {@link #scheduleCampaign} only flips status; audience expansion/enqueue
 * belongs to the background scheduler (Task 7).
 */
@Component
public class CommunicationsAdmin {

    private static final String CAMPAIGN_COLUMNS = """
            id, name, campaign_type, topic, status, subject, body_html, body_text,
            template_id, promotion_id, scheduled_at, started_at, completed_at,
            cancelled_at, total_recipients, sent_count, failed_count, error,
            created_by, cancelled_by, created_at, updated_at
            """;

    private static final String TEMPLATE_COLUMNS = """
            id, code, name, subject, body_html, body_text, variables, is_active,
            created_at, updated_at
            """;

    private static final String SUPPRESSION_COLUMNS = """
            id, email, reason, source, notes, created_at
            """;

    private final JdbcTemplate jdbc;
    private final AuditWriter audit;
    private final ObjectMapper objectMapper;
    private final SmtpTransport transport;
    private final UnsubscribeTokens tokens;
    private final GuestComms guestComms;

    public CommunicationsAdmin(JdbcTemplate jdbc, AuditWriter audit,
            ObjectMapper objectMapper, SmtpTransport transport,
            UnsubscribeTokens tokens, GuestComms guestComms) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.transport = transport;
        this.tokens = tokens;
        this.guestComms = guestComms;
    }

    /** {@code normalize_page}: page ≥ 1, page_size clamped to 1..=100, default 20. */
    private static long[] normalizePage(Long page, Long pageSize) {
        long p = page == null ? 1 : Math.max(1, page);
        long ps = pageSize == null ? 20 : Math.min(100, Math.max(1, pageSize));
        return new long[] {p, ps};
    }

    // ------------------------------------------------------------------
    // Row mappers
    // ------------------------------------------------------------------

    private EmailCampaign campaignFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
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

    private EmailTemplate templateFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
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

    private EmailSuppression suppressionFromRow(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new EmailSuppression(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("reason"),
                rs.getString("source"),
                rs.getString("notes"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    /** Masked summary for one raw delivery row selected by the feed queries. */
    private DeliverySummary summaryFromRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DeliverySummary(
                rs.getLong("id"),
                (Long) rs.getObject("campaign_id"),
                rs.getString("kind"),
                rs.getLong("guest_id"),
                rs.getString("topic"),
                rs.getString("subject"),
                CommsValidation.maskEmail(rs.getString("recipient_email")),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getString("last_error"),
                rs.getObject("sent_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    // ------------------------------------------------------------------
    // Campaigns
    // ------------------------------------------------------------------

    private EmailCampaign requireCampaign(long id) {
        List<EmailCampaign> rows = jdbc.query(
                "SELECT " + CAMPAIGN_COLUMNS + " FROM email_campaigns WHERE id = ?",
                (rs, i) -> campaignFromRow(rs), id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Campaign not found");
        }
        return rows.get(0);
    }

    private Optional<String> promotionStatus(long promotionId) {
        return jdbc.query(
                        "SELECT status FROM promotions WHERE id = ?",
                        (rs, i) -> rs.getString(1), promotionId)
                .stream().findFirst();
    }

    public CampaignListResponse listCampaigns(
            String status, String campaignType, Long page, Long pageSize) {
        long[] pg = normalizePage(page, pageSize);
        String statusFilter =
                status == null || status.trim().isEmpty() ? null
                        : status.trim().toLowerCase(java.util.Locale.ROOT);
        String typeFilter =
                campaignType == null || campaignType.trim().isEmpty() ? null
                        : campaignType.trim().toLowerCase(java.util.Locale.ROOT);
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM email_campaigns
                WHERE (?::text IS NULL OR status = ?)
                  AND (?::text IS NULL OR campaign_type = ?)
                """, Long.class, statusFilter, statusFilter, typeFilter, typeFilter);
        List<EmailCampaign> items = jdbc.query(
                "SELECT " + CAMPAIGN_COLUMNS + " FROM email_campaigns"
                        + " WHERE (?::text IS NULL OR status = ?)"
                        + " AND (?::text IS NULL OR campaign_type = ?)"
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> campaignFromRow(rs),
                statusFilter, statusFilter, typeFilter, typeFilter,
                pg[1], (pg[0] - 1) * pg[1]);
        return new CampaignListResponse(items, total, pg[0], pg[1]);
    }

    public EmailCampaign getCampaign(long id) {
        return requireCampaign(id);
    }

    @Transactional
    public EmailCampaign createCampaign(long actorId, CampaignInput input,
            String ipAddress, String userAgent) {
        CampaignDraft draft = CommsValidation.validateCampaignInput(input);
        if (draft.promotionId() != null && promotionStatus(draft.promotionId()).isEmpty()) {
            throw ApiError.badRequest("Unknown promotion");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO email_campaigns
                    (name, campaign_type, topic, status, subject, body_html,
                     body_text, template_id, promotion_id, created_by)
                VALUES (?, ?, ?, 'draft', ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class,
                draft.name(), draft.campaignType(), draft.topic(), draft.subject(),
                draft.bodyHtml(), draft.bodyText(), draft.templateId(),
                draft.promotionId(), actorId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("campaign_type", draft.campaignType());
        details.put("topic", draft.topic());
        details.put("promotion_id", draft.promotionId());
        audit.event(actorId, "campaign.created", "email_campaign", id,
                details, ipAddress, userAgent);
        return requireCampaign(id);
    }

    @Transactional
    public EmailCampaign updateCampaign(long actorId, long id, CampaignInput input,
            String ipAddress, String userAgent) {
        CampaignDraft draft = CommsValidation.validateCampaignInput(input);
        EmailCampaign existing = requireCampaign(id);
        if (!"draft".equals(existing.status())) {
            throw ApiError.conflict("Only draft campaigns can be edited");
        }
        int updated = jdbc.update("""
                UPDATE email_campaigns SET
                    name = ?, campaign_type = ?, topic = ?, subject = ?,
                    body_html = ?, body_text = ?, template_id = ?,
                    promotion_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'draft'
                """,
                draft.name(), draft.campaignType(), draft.topic(), draft.subject(),
                draft.bodyHtml(), draft.bodyText(), draft.templateId(),
                draft.promotionId(), id);
        if (updated == 0) {
            throw ApiError.conflict("Only draft campaigns can be edited");
        }
        audit.event(actorId, "campaign.updated", "email_campaign", id,
                Map.of("campaign_type", draft.campaignType(), "topic", draft.topic()),
                ipAddress, userAgent);
        return requireCampaign(id);
    }

    private static Map<String, String> sampleVars(EmailTemplate template) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (String name : template.variables()) {
            vars.put(name, "[" + name + "]");
        }
        return vars;
    }

    /** The linked template rendered with sample values, or the campaign's own body. */
    private String renderCampaignBody(EmailCampaign campaign) {
        if (campaign.templateId() == null) {
            return campaign.bodyHtml();
        }
        EmailTemplate template = getTemplate(campaign.templateId())
                .orElseThrow(() -> ApiError.notFound("Template not found"));
        return CommsValidation.renderTemplate(
                template.bodyHtml(), sampleVars(template), template.variables());
    }

    public PreviewResponse previewCampaign(long id) {
        EmailCampaign campaign = requireCampaign(id);
        // Stored campaign HTML is authored by communications:compose and kept
        // verbatim for SMTP delivery; the browser preview gets the whitelist
        // sanitizer because it renders via dangerouslySetInnerHTML.
        String bodyHtml = Jsoup.clean(renderCampaignBody(campaign), Safelist.relaxed());
        AudienceCount audience = countAudienceForTopic(campaign.topic());
        return new PreviewResponse(campaign.subject(), bodyHtml, audience);
    }

    /**
     * {@code test_send_campaign}: ONE rendered test email sent synchronously —
     * not via the outbox — gated on communications:send.
     */
    public void testSendCampaign(long actorId, long id, TestSendInput input,
            String ipAddress, String userAgent) {
        String recipient = CommsValidation.validateEmail(input.recipientEmail());
        EmailCampaign campaign = requireCampaign(id);
        if (!transport.isConfigured()) {
            throw ApiError.conflict(
                    "Email transport is not configured; set SMTP_* environment variables");
        }
        String bodyHtml = renderCampaignBody(campaign);
        try {
            transport.send(new SmtpTransport.OutgoingEmail(
                    recipient, "[TEST] " + campaign.subject(), bodyHtml, campaign.bodyText()));
        } catch (ApiError e) {
            throw e;
        } catch (Exception e) {
            throw ApiError.internal("Test send failed: " + e.getMessage());
        }
        audit.event(actorId, "campaign.test_sent", "email_campaign", id,
                null, ipAddress, userAgent);
    }

    @Transactional
    public EmailCampaign scheduleCampaign(long actorId, long id, ScheduleCampaignInput input,
            String ipAddress, String userAgent) {
        if (!transport.isConfigured()) {
            throw ApiError.conflict(
                    "Email transport is not configured; set SMTP_* environment variables "
                            + "before scheduling");
        }
        EmailCampaign campaign = requireCampaign(id);
        if (!"draft".equals(campaign.status())) {
            throw ApiError.conflict("Only draft campaigns can be scheduled");
        }
        if ("promotion".equals(campaign.campaignType())) {
            if (campaign.promotionId() == null) {
                throw ApiError.conflict("Promotion campaigns require a promotion");
            }
            String status = promotionStatus(campaign.promotionId())
                    .orElseThrow(() -> ApiError.conflict("Linked promotion no longer exists"));
            if (!"published".equals(status)) {
                throw ApiError.conflict("Linked promotion must be published before sending");
            }
        }
        OffsetDateTime scheduledAt = input.scheduledAt() == null
                ? OffsetDateTime.now() : input.scheduledAt();
        if (scheduledAt.isBefore(OffsetDateTime.now().minusMinutes(1))) {
            throw ApiError.badRequest("scheduled_at cannot be in the past");
        }
        int updated = jdbc.update("""
                UPDATE email_campaigns
                SET status = 'scheduled', scheduled_at = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'draft'
                """, scheduledAt, id);
        if (updated == 0) {
            throw ApiError.conflict("Only draft campaigns can be scheduled");
        }
        audit.event(actorId, "campaign.scheduled", "email_campaign", id,
                Map.of("scheduled_at", scheduledAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
                ipAddress, userAgent);
        return requireCampaign(id);
    }

    @Transactional
    public EmailCampaign cancelCampaign(long actorId, long id,
            String ipAddress, String userAgent) {
        requireCampaign(id);
        int updated = jdbc.update("""
                UPDATE email_campaigns
                SET status = 'cancelled', cancelled_at = CURRENT_TIMESTAMP,
                    cancelled_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('scheduled', 'running')
                """, actorId, id);
        if (updated == 0) {
            throw ApiError.conflict("Only scheduled or running campaigns can be cancelled");
        }
        audit.event(actorId, "campaign.cancelled", "email_campaign", id,
                null, ipAddress, userAgent);
        return requireCampaign(id);
    }

    public DeliveryListResponse listCampaignDeliveries(
            long campaignId, Long page, Long pageSize) {
        requireCampaign(campaignId);
        long[] pg = normalizePage(page, pageSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_deliveries WHERE campaign_id = ?",
                Long.class, campaignId);
        List<DeliverySummary> items = jdbc.query("""
                SELECT id, campaign_id, kind, guest_id, topic, recipient_email,
                       subject, status, attempts, last_error, sent_at, created_at
                FROM email_deliveries
                WHERE campaign_id = ?
                ORDER BY id DESC LIMIT ? OFFSET ?
                """, (rs, i) -> summaryFromRow(rs), campaignId, pg[1], (pg[0] - 1) * pg[1]);
        return new DeliveryListResponse(items, total, pg[0], pg[1]);
    }

    /**
     * {@code list_delivery_feed}: admin notification-center feed — recent
     * deliveries across all campaigns, filterable by derived tier and exact
     * status, with a global queued+sending count for the bell badge.
     */
    public DeliveryFeedResponse listDeliveryFeed(
            String tier, String status, Long page, Long pageSize) {
        List<String> tiers = List.of("all", "transactional", "marketing");
        String resolvedTier = tier == null ? "all" : tier;
        if (!tiers.contains(resolvedTier)) {
            throw ApiError.badRequest("Unknown tier '" + resolvedTier
                    + "'. Expected one of: " + String.join(", ", tiers));
        }
        if (status != null) {
            CommsValidation.validateDeliveryStatus(status);
        }
        long[] pg = normalizePage(page, pageSize);
        String[] kinds = switch (resolvedTier) {
            case "transactional" -> CommsValidation.TRANSACTIONAL_KINDS.toArray(String[]::new);
            case "marketing" -> CommsValidation.MARKETING_KINDS.toArray(String[]::new);
            default -> new String[0];
        };
        long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM email_deliveries
                WHERE (cardinality(?::text[]) = 0 OR kind = ANY(?))
                  AND (?::text IS NULL OR status = ?)
                """, Long.class, kinds, kinds, status, status);
        long unread = jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_deliveries WHERE status IN ('queued', 'sending')",
                Long.class);
        List<DeliveryFeedItem> items = jdbc.query("""
                SELECT id, campaign_id, kind, guest_id, topic, recipient_email,
                       subject, status, attempts, last_error, sent_at, created_at
                FROM email_deliveries
                WHERE (cardinality(?::text[]) = 0 OR kind = ANY(?))
                  AND (?::text IS NULL OR status = ?)
                ORDER BY id DESC LIMIT ? OFFSET ?
                """, (rs, i) -> {
            DeliverySummary s = summaryFromRow(rs);
            return new DeliveryFeedItem(
                    s.id(), s.campaignId(), s.kind(), s.guestId(), s.topic(), s.subject(),
                    s.recipientMasked(), s.status(), s.attempts(), s.lastError(),
                    s.sentAt(), s.createdAt(), CommsValidation.deliveryTier(s.kind()));
        }, kinds, kinds, status, status, pg[1], (pg[0] - 1) * pg[1]);
        return new DeliveryFeedResponse(items, total, unread, pg[0], pg[1]);
    }

    // ------------------------------------------------------------------
    // Templates
    // ------------------------------------------------------------------

    public List<EmailTemplate> listTemplates() {
        return jdbc.query(
                "SELECT " + TEMPLATE_COLUMNS + " FROM email_templates ORDER BY code",
                (rs, i) -> templateFromRow(rs));
    }

    public Optional<EmailTemplate> getTemplate(long id) {
        return jdbc.query(
                        "SELECT " + TEMPLATE_COLUMNS + " FROM email_templates WHERE id = ?",
                        (rs, i) -> templateFromRow(rs), id)
                .stream().findFirst();
    }

    private Optional<EmailTemplate> getTemplateByCode(String code) {
        return jdbc.query(
                        "SELECT " + TEMPLATE_COLUMNS + " FROM email_templates WHERE code = ?",
                        (rs, i) -> templateFromRow(rs), code)
                .stream().findFirst();
    }

    private String variablesJson(List<String> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Transactional
    public EmailTemplate createTemplate(long actorId, TemplateInput input,
            String ipAddress, String userAgent) {
        TemplateDraft draft = CommsValidation.validateTemplateInput(input);
        if (getTemplateByCode(draft.code()).isPresent()) {
            throw ApiError.conflict("Template code already exists");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO email_templates
                    (code, name, subject, body_html, body_text, variables, is_active)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                RETURNING id
                """, Long.class,
                draft.code(), draft.name(), draft.subject(), draft.bodyHtml(),
                draft.bodyText(), variablesJson(draft.variables()), draft.isActive());
        audit.event(actorId, "email_template.created", "email_template", id,
                Map.of("code", draft.code()), ipAddress, userAgent);
        return getTemplate(id)
                .orElseThrow(() -> ApiError.internal("Template vanished after insert"));
    }

    @Transactional
    public EmailTemplate updateTemplate(long actorId, long id, TemplateInput input,
            String ipAddress, String userAgent) {
        TemplateDraft draft = CommsValidation.validateTemplateInput(input);
        Optional<EmailTemplate> existing = getTemplateByCode(draft.code());
        if (existing.isPresent() && existing.get().id() != id) {
            throw ApiError.conflict("Template code already exists");
        }
        int updated = jdbc.update("""
                UPDATE email_templates SET
                    code = ?, name = ?, subject = ?, body_html = ?,
                    body_text = ?, variables = CAST(? AS jsonb), is_active = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                draft.code(), draft.name(), draft.subject(), draft.bodyHtml(),
                draft.bodyText(), variablesJson(draft.variables()), draft.isActive(), id);
        if (updated == 0) {
            throw ApiError.notFound("Template not found");
        }
        audit.event(actorId, "email_template.updated", "email_template", id,
                Map.of("code", draft.code()), ipAddress, userAgent);
        return getTemplate(id)
                .orElseThrow(() -> ApiError.notFound("Template not found"));
    }

    @Transactional
    public void deactivateTemplate(long actorId, long id, String ipAddress, String userAgent) {
        int updated = jdbc.update(
                "UPDATE email_templates SET is_active = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ?",
                false, id);
        if (updated == 0) {
            throw ApiError.notFound("Template not found");
        }
        audit.event(actorId, "email_template.deactivated", "email_template", id,
                null, ipAddress, userAgent);
    }

    // ------------------------------------------------------------------
    // Audience + suppressions
    // ------------------------------------------------------------------

    /** {@code count_audience_for_topic}: server-side counts; never returns recipients. */
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

    public AudienceCount audienceCount(String topic) {
        return countAudienceForTopic(CommsValidation.validateTopic(topic));
    }

    public SuppressionListResponse listSuppressions(Long page, Long pageSize) {
        long[] pg = normalizePage(page, pageSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM email_suppressions", Long.class);
        List<EmailSuppression> items = jdbc.query(
                "SELECT " + SUPPRESSION_COLUMNS + " FROM email_suppressions"
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                (rs, i) -> suppressionFromRow(rs), pg[1], (pg[0] - 1) * pg[1]);
        return new SuppressionListResponse(items, total, pg[0], pg[1]);
    }

    @Transactional
    public void addSuppression(long actorId, SuppressionInput input,
            String ipAddress, String userAgent) {
        SuppressionDraft draft = CommsValidation.validateSuppressionInput(input);
        insertSuppression(draft, "staff");
        audit.event(actorId, "suppression.added", "email_suppression", null,
                Map.of("email_masked", CommsValidation.maskEmail(draft.email()),
                        "reason", draft.reason()),
                ipAddress, userAgent);
    }

    private void insertSuppression(SuppressionDraft draft, String source) {
        jdbc.update("""
                INSERT INTO email_suppressions (email, reason, source, notes)
                VALUES (LOWER(?), ?, ?, ?)
                ON CONFLICT (email) DO UPDATE SET
                    reason = EXCLUDED.reason,
                    source = EXCLUDED.source,
                    notes = EXCLUDED.notes
                """, draft.email(), draft.reason(), source, draft.notes());
    }

    @Transactional
    public void removeSuppression(long actorId, String email,
            String ipAddress, String userAgent) {
        String normalized = CommsValidation.validateEmail(email);
        int removed = jdbc.update(
                "DELETE FROM email_suppressions WHERE email = LOWER(?)", normalized);
        if (removed == 0) {
            throw ApiError.notFound("Suppression not found");
        }
        audit.event(actorId, "suppression.removed", "email_suppression", null,
                Map.of("email_masked", CommsValidation.maskEmail(normalized)),
                ipAddress, userAgent);
    }

    // ------------------------------------------------------------------
    // Guest consent (staff-facing)
    // ------------------------------------------------------------------

    public ConsentStatusResponse guestConsentStatus(long guestId) {
        return guestComms.guestConsentStatus(guestId);
    }

    public ConsentStatusResponse recordStaffConsent(long actorId, long guestId,
            GuestComms.PreferenceUpdateInput input, String ipAddress, String userAgent) {
        return guestComms.recordStaffConsent(actorId, guestId, input, ipAddress, userAgent);
    }

    // ------------------------------------------------------------------
    // Public unsubscribe
    // ------------------------------------------------------------------

    private long guestIdFromToken(String token) {
        Long guestId = tokens.verify(token);
        if (guestId == null) {
            throw ApiError.notFound("Invalid unsubscribe link");
        }
        return guestId;
    }

    public PreferencesResponse unsubscribeView(String token) {
        return guestComms.getPreferences(guestIdFromToken(token));
    }

    /**
     * {@code unsubscribe_apply}: validated subscription changes in one
     * transaction, then — for a global unsubscribe — the suppression row plus
     * {@code consent.opt_out} audit in a second transaction.
     */
    public PreferencesResponse unsubscribeApply(String token, UnsubscribeApplyInput input,
            String ipAddress, String userAgent) {
        long guestId = guestIdFromToken(token);
        String email = guestComms.guestEmail(guestId);
        boolean global = input.global() != null && input.global();
        List<SubscriptionUpdateInput> changes;
        if (global) {
            changes = CommsValidation.TOPICS.stream()
                    .map(t -> new SubscriptionUpdateInput(t, false))
                    .toList();
        } else {
            if (input.topic() == null) {
                throw ApiError.badRequest("topic or global is required");
            }
            changes = List.of(new SubscriptionUpdateInput(
                    CommsValidation.validateTopic(input.topic()), false));
        }
        guestComms.applyUnsubscribeChanges(guestId, changes, ipAddress, userAgent);
        if (global && email != null) {
            String normalized;
            try {
                normalized = CommsValidation.validateEmail(email);
            } catch (ApiError e) {
                normalized = null;
            }
            if (normalized != null) {
                guestComms.recordGlobalUnsubscribe(guestId,
                        new SuppressionDraft(normalized, "unsubscribe", null),
                        ipAddress, userAgent);
            }
        }
        return guestComms.getPreferences(guestId);
    }
}
