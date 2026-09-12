package com.hotelapp.communications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.communications.CommsModels.CampaignInput;
import com.hotelapp.communications.CommsModels.SuppressionInput;
import com.hotelapp.communications.CommsModels.TemplateInput;
import com.hotelapp.communications.CommsValidation.CampaignDraft;
import com.hotelapp.communications.CommsValidation.TemplateDraft;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the pure helpers mirrored from
 * {@code modules/communications/validation.rs}, {@code tokens.rs}, and
 * {@code transport.rs}.
 */
class CommunicationsContractTest {

    // ---- delivery tiers (validation.rs tests) ------------------------------

    @Test
    void transactionalKindsBypassTopicSubscriptions() {
        for (String kind : CommsValidation.TRANSACTIONAL_KINDS) {
            assertFalse(CommsValidation.requiresTopicSubscription(kind),
                    kind + " is transactional and must not require a subscription");
        }
        assertTrue(CommsValidation.requiresTopicSubscription("campaign"));
        assertTrue(CommsValidation.requiresTopicSubscription("birthday_voucher"));
        assertTrue(CommsValidation.requiresTopicSubscription("something_new"));
    }

    @Test
    void deliveryTierLabels() {
        assertEquals("transactional", CommsValidation.deliveryTier("booking_confirmation"));
        assertEquals("marketing", CommsValidation.deliveryTier("campaign"));
        assertEquals("marketing", CommsValidation.deliveryTier("unknown_future_kind"));
    }

    // ---- campaign validation ------------------------------------------------

    private static CampaignInput campaignInput(String campaignType, Long promotionId) {
        return new CampaignInput("Guest update", campaignType, "Your upcoming stay",
                "<p>Welcome</p>", null, null, promotionId);
    }

    @Test
    void announcementDiscardsAnIrrelevantPromotionId() {
        CampaignDraft draft =
                CommsValidation.validateCampaignInput(campaignInput("announcement", 999L));
        assertEquals("announcement", draft.campaignType());
        assertEquals("announcement", draft.topic());
        assertNull(draft.promotionId());
    }

    @Test
    void promotionRequiresAPositivePromotionId() {
        for (Long promotionId : new Long[] {null, 0L, -1L}) {
            assertThrows(ApiError.class, () ->
                    CommsValidation.validateCampaignInput(
                            campaignInput("promotion", promotionId)));
        }
        assertEquals(4L, CommsValidation.validateCampaignInput(campaignInput("promotion", 4L))
                .promotionId());
    }

    @Test
    void campaignValidationRejectsUnknownTypeAndEmptyFields() {
        assertThrows(ApiError.class, () ->
                CommsValidation.validateCampaignInput(campaignInput("newsletter", null)));
        assertThrows(ApiError.class, () ->
                CommsValidation.validateCampaignInput(
                        new CampaignInput("", "announcement", "s", "<p>x</p>", null, null, null)));
        assertThrows(ApiError.class, () ->
                CommsValidation.validateCampaignInput(
                        new CampaignInput("n", "announcement", "s", "   ", null, null, null)));
    }

    // ---- template rendering --------------------------------------------------

    @Test
    void renderSubstitutesAndEscapes() {
        String out = CommsValidation.renderTemplate(
                "Hi {{name}}!", Map.of("name", "<b>Amy & Co</b>"), List.of("name"));
        assertEquals("Hi &lt;b&gt;Amy &amp; Co&lt;/b&gt;!", out);
    }

    @Test
    void renderRejectsUnknownVariable() {
        assertThrows(ApiError.class, () ->
                CommsValidation.renderTemplate("{{evil}}", Map.of(), List.of()));
    }

    @Test
    void renderRejectsUnterminatedToken() {
        assertThrows(ApiError.class, () ->
                CommsValidation.renderTemplate("Hi {{name", Map.of(), List.of("name")));
    }

    @Test
    void templateValidation() {
        TemplateDraft draft = CommsValidation.validateTemplateInput(new TemplateInput(
                "welcome_v1", "Welcome", "Hi {{guest}}", "<p>{{guest}}</p>", null,
                List.of("guest"), null));
        assertTrue(draft.isActive());
        assertEquals(List.of("guest"), draft.variables());

        assertThrows(ApiError.class, () ->
                CommsValidation.validateTemplateInput(new TemplateInput(
                        "Bad Code", "n", "s", "<p>x</p>", null, null, null)));
        assertThrows(ApiError.class, () ->
                CommsValidation.validateTemplateInput(new TemplateInput(
                        "ok_code", "n", "s", "<p>x</p>", null, List.of("not an ident"), null)));
    }

    // ---- email + masking ------------------------------------------------------

    @Test
    void emailValidationNormalizes() {
        assertEquals("jane@example.com", CommsValidation.validateEmail(" Jane@Example.COM "));
        assertThrows(ApiError.class, () -> CommsValidation.validateEmail("nope"));
        assertThrows(ApiError.class, () -> CommsValidation.validateEmail("a b@x.com"));
        assertThrows(ApiError.class, () -> CommsValidation.validateEmail(null));
    }

    @Test
    void maskEmailKeepsFirstCharAndDomain() {
        assertEquals("j•••@example.com", CommsValidation.maskEmail("jane.doe@example.com"));
        assertEquals("•••", CommsValidation.maskEmail("not-an-email"));
        assertEquals("•••@x.com", CommsValidation.maskEmail("@x.com"));
    }

    // ---- topics / statuses / suppressions ------------------------------------

    @Test
    void topicAndStatusValidation() {
        assertEquals("promotion", CommsValidation.validateTopic(" Promotion "));
        assertThrows(ApiError.class, () -> CommsValidation.validateTopic("billing"));
        assertEquals("queued", CommsValidation.validateDeliveryStatus("queued"));
        ApiError error = assertThrows(ApiError.class,
                () -> CommsValidation.validateDeliveryStatus("delivered"));
        assertTrue(error.getMessage().contains("delivered"));
    }

    @Test
    void suppressionValidation() {
        var draft = CommsValidation.validateSuppressionInput(
                new SuppressionInput(" A@B.COM ", "Manual", "  spam trap  "));
        assertEquals("a@b.com", draft.email());
        assertEquals("manual", draft.reason());
        assertEquals("spam trap", draft.notes());
        assertThrows(ApiError.class, () ->
                CommsValidation.validateSuppressionInput(
                        new SuppressionInput("a@b.com", "oops", null)));
    }

    // ---- unsubscribe tokens (tokens.rs) ---------------------------------------

    private static UnsubscribeTokens tokens() {
        AppProperties properties = new AppProperties();
        properties.setJwtSecret("test-secret-key-that-is-long-enough");
        return new UnsubscribeTokens(properties);
    }

    @Test
    void unsubscribeTokenRoundTrips() {
        UnsubscribeTokens tokens = tokens();
        String token = tokens.sign(42);
        assertEquals(42L, tokens.verify(token));
    }

    @Test
    void unsubscribeTokenRejectsTampering() {
        UnsubscribeTokens tokens = tokens();
        String token = tokens.sign(42);
        assertNull(tokens.verify(token + "x"));
        assertNull(tokens.verify("AAAA." + token.substring(token.indexOf('.') + 1)));
        assertNull(tokens.verify("no-dot"));
        assertNull(tokens.verify("not_base64!.deadbeef"));
    }

    @Test
    void unsubscribeTokenRejectsDifferentSecret() {
        String token = tokens().sign(42);
        AppProperties other = new AppProperties();
        other.setJwtSecret("a-different-secret-key-altogether");
        assertNull(new UnsubscribeTokens(other).verify(token));
    }

    // ---- SMTP config (transport.rs tests) --------------------------------------

    @Test
    void smtpConfigRequiresHostAndSenderIdentity() {
        java.util.function.Function<String, String> env = key -> switch (key) {
            case "SMTP_HOST" -> "smtp.example.com";
            case "SMTP_FROM_EMAIL" -> "sender@example.com";
            default -> null;
        };
        assertNull(SmtpTransport.SmtpConfig.fromValues(k -> null));
        assertNull(SmtpTransport.SmtpConfig.fromValues(k ->
                "SMTP_FROM_EMAIL".equals(k) ? "sender@example.com" : null));
        assertNull(SmtpTransport.SmtpConfig.fromValues(k ->
                "SMTP_HOST".equals(k) ? "smtp.example.com" : null));

        SmtpTransport.SmtpConfig configured = SmtpTransport.SmtpConfig.fromValues(env);
        assertNotNull(configured);
        assertEquals("smtp.example.com", configured.host());
        assertEquals("sender@example.com", configured.fromEmail());
        assertEquals(587, configured.port());
        assertEquals("starttls", configured.security());
    }
}
