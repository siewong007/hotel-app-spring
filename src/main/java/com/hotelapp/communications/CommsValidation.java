package com.hotelapp.communications;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.text.Sanitizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port of {@code modules/communications/validation.rs}: input validation and
 * {@code {{variable}}} template rendering. Pure static helpers; all errors use
 * the upstream message strings.
 */
public final class CommsValidation {

    private CommsValidation() {
    }

    public static final List<String> TOPICS =
            List.of("announcement", "promotion", "birthday_voucher");
    public static final List<String> CAMPAIGN_TYPES = List.of("announcement", "promotion");
    public static final List<String> SUPPRESSION_REASONS =
            List.of("unsubscribe", "bounce", "complaint", "manual");

    /**
     * Service-lifecycle delivery kinds: they bypass the per-topic subscription
     * gate in the worker, while hard suppressions still apply.
     */
    public static final List<String> TRANSACTIONAL_KINDS = List.of(
            "booking_confirmation",
            "online_room_assignment",
            "payment_receipt_request",
            "payment_rejected",
            "checkout_receipt",
            "pre_arrival_reminder");

    public static final List<String> MARKETING_KINDS = List.of("campaign", "birthday_voucher");

    /** {@code email_deliveries_status_check} statuses. */
    public static final List<String> DELIVERY_STATUSES =
            List.of("queued", "sending", "sent", "failed", "suppressed", "cancelled");

    public static final int MAX_BODY_CHARS = 200_000;

    /** {@code requires_topic_subscription} — unknown kinds fail closed. */
    public static boolean requiresTopicSubscription(String kind) {
        return !TRANSACTIONAL_KINDS.contains(kind);
    }

    /** {@code delivery_tier}: transactional service mail vs marketing. */
    public static String deliveryTier(String kind) {
        return TRANSACTIONAL_KINDS.contains(kind) ? "transactional" : "marketing";
    }

    public record CampaignDraft(
            String name,
            String campaignType,
            String topic,
            String subject,
            String bodyHtml,
            String bodyText,
            Long templateId,
            Long promotionId) {
    }

    public record TemplateDraft(
            String code,
            String name,
            String subject,
            String bodyHtml,
            String bodyText,
            List<String> variables,
            boolean isActive) {
    }

    public record SuppressionDraft(String email, String reason, String notes) {
    }

    private static String sanitizeRequiredText(String value, String field, int min, int max) {
        String cleaned = Sanitizer.sanitizeNotes(value == null ? "" : value).trim();
        int len = cleaned.codePointCount(0, cleaned.length());
        if (len < min || len > max) {
            throw ApiError.badRequest(
                    field + " must be between " + min + " and " + max + " characters");
        }
        return cleaned;
    }

    private static String sanitizeOptionalText(String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = Sanitizer.sanitizeNotes(value).trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.codePointCount(0, cleaned.length()) > max) {
            throw ApiError.badRequest(field + " cannot exceed " + max + " characters");
        }
        return cleaned;
    }

    /**
     * {@code validate_body}: staff-authored HTML is intentionally NOT stripped,
     * only bounded. Substituted values are always HTML-escaped by
     * {@link #renderTemplate}.
     */
    private static String validateBody(String value, String field) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) {
            throw ApiError.badRequest(field + " is required");
        }
        if (cleaned.codePointCount(0, cleaned.length()) > MAX_BODY_CHARS) {
            throw ApiError.badRequest(field + " cannot exceed " + MAX_BODY_CHARS + " characters");
        }
        return cleaned;
    }

    private static boolean isIdentifier(String value) {
        if (value.isEmpty() || !isAsciiLower(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!isAsciiLower(c) && !isAsciiDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLower(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static String validateDeliveryStatus(String status) {
        if (DELIVERY_STATUSES.contains(status)) {
            return status;
        }
        throw ApiError.badRequest("Unknown delivery status '" + status + "'.");
    }

    public static String validateTopic(String topic) {
        String normalized = topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT);
        if (TOPICS.contains(normalized)) {
            return normalized;
        }
        throw ApiError.badRequest("Unsupported notification topic");
    }

    public static String validateEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        int len = email.codePointCount(0, email.length());
        boolean shapeOk = len >= 3 && len <= 255 && !email.chars().anyMatch(Character::isWhitespace);
        if (shapeOk) {
            int at = email.indexOf('@');
            shapeOk = at > 0 && email.substring(at + 1).contains(".");
        }
        if (!shapeOk) {
            throw ApiError.badRequest("Invalid email address");
        }
        return email;
    }

    /** {@code mask_email}: {@code jane.doe@example.com} → {@code j•••@example.com}. */
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at < 0) {
            return "•••";
        }
        String local = email.substring(0, at);
        String first = local.isEmpty() ? "" : String.valueOf(local.charAt(0));
        return first + "•••" + email.substring(at);
    }

    public static CampaignDraft validateCampaignInput(CommsModels.CampaignInput input) {
        String campaignType =
                input.campaignType() == null ? "" : input.campaignType().trim().toLowerCase(Locale.ROOT);
        if (!CAMPAIGN_TYPES.contains(campaignType)) {
            throw ApiError.badRequest("Unsupported campaign type");
        }
        Long promotionId;
        if ("promotion".equals(campaignType)) {
            if (input.promotionId() == null || input.promotionId() <= 0) {
                throw ApiError.badRequest("Promotion campaigns require a valid promotion");
            }
            promotionId = input.promotionId();
        } else {
            promotionId = null;
        }
        return new CampaignDraft(
                sanitizeRequiredText(input.name(), "name", 1, 160),
                campaignType,
                campaignType,
                sanitizeRequiredText(input.subject(), "subject", 1, 255),
                validateBody(input.bodyHtml(), "body_html"),
                sanitizeOptionalText(input.bodyText(), "body_text", MAX_BODY_CHARS),
                input.templateId(),
                promotionId);
    }

    public static TemplateDraft validateTemplateInput(CommsModels.TemplateInput input) {
        String code = input.code() == null ? "" : input.code().trim().toLowerCase(Locale.ROOT);
        if (code.isEmpty() || code.codePointCount(0, code.length()) > 50 || !isIdentifier(code)) {
            throw ApiError.badRequest(
                    "Template code must be lowercase letters, digits, or underscores (max 50)");
        }
        List<String> variables = input.variables() == null ? List.of() : input.variables();
        for (String variable : variables) {
            if (!isIdentifier(variable) || variable.codePointCount(0, variable.length()) > 50) {
                throw ApiError.badRequest("Invalid template variable name: " + variable);
            }
        }
        return new TemplateDraft(
                code,
                sanitizeRequiredText(input.name(), "name", 1, 100),
                sanitizeRequiredText(input.subject(), "subject", 1, 255),
                validateBody(input.bodyHtml(), "body_html"),
                sanitizeOptionalText(input.bodyText(), "body_text", MAX_BODY_CHARS),
                variables,
                input.isActive() == null || input.isActive());
    }

    public static SuppressionDraft validateSuppressionInput(CommsModels.SuppressionInput input) {
        String reason = input.reason() == null ? "" : input.reason().trim().toLowerCase(Locale.ROOT);
        if (!SUPPRESSION_REASONS.contains(reason)) {
            throw ApiError.badRequest("Unsupported suppression reason");
        }
        return new SuppressionDraft(
                validateEmail(input.email()),
                reason,
                sanitizeOptionalText(input.notes(), "notes", 1000));
    }

    public static String htmlEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * {@code render_template}: substitutes {@code {{variable}}} tokens. Every
     * referenced variable must be in {@code allowed} and have a value in
     * {@code vars}; values are HTML-escaped so they can never inject markup.
     */
    public static String renderTemplate(
            String body, Map<String, String> vars, List<String> allowed) {
        StringBuilder rendered = new StringBuilder(body.length());
        int cursor = 0;
        while (true) {
            int start = body.indexOf("{{", cursor);
            if (start < 0) {
                break;
            }
            rendered.append(body, cursor, start);
            int end = body.indexOf("}}", start + 2);
            if (end < 0) {
                throw ApiError.badRequest("Unterminated template variable");
            }
            String name = body.substring(start + 2, end).trim();
            if (!allowed.contains(name)) {
                throw ApiError.badRequest("Unknown template variable: " + name);
            }
            String value = vars.get(name);
            if (value == null) {
                throw ApiError.badRequest("Missing value for template variable: " + name);
            }
            rendered.append(htmlEscape(value));
            cursor = end + 2;
        }
        rendered.append(body, cursor, body.length());
        return rendered.toString();
    }
}
