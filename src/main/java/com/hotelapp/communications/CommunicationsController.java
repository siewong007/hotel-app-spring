package com.hotelapp.communications;

import com.hotelapp.communications.CommsModels.AudienceCount;
import com.hotelapp.communications.CommsModels.CampaignInput;
import com.hotelapp.communications.CommsModels.CampaignListResponse;
import com.hotelapp.communications.CommsModels.ConsentStatusResponse;
import com.hotelapp.communications.CommsModels.DeliveryFeedResponse;
import com.hotelapp.communications.CommsModels.DeliveryListResponse;
import com.hotelapp.communications.CommsModels.EmailCampaign;
import com.hotelapp.communications.CommsModels.EmailTemplate;
import com.hotelapp.communications.CommsModels.PreviewResponse;
import com.hotelapp.communications.CommsModels.ScheduleCampaignInput;
import com.hotelapp.communications.CommsModels.SuppressionInput;
import com.hotelapp.communications.CommsModels.SuppressionListResponse;
import com.hotelapp.communications.CommsModels.TemplateInput;
import com.hotelapp.communications.CommsModels.TestSendInput;
import com.hotelapp.communications.CommsModels.UnsubscribeApplyInput;
import com.hotelapp.communications.GuestComms.PreferenceUpdateInput;
import com.hotelapp.communications.GuestComms.PreferencesResponse;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.core.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for {@code modules/communications/routes.rs}: staff campaign /
 * template / audience / suppression / consent administration plus the public
 * token-authenticated unsubscribe endpoints. Guest-portal notification
 * preferences live in {@link PreferencesController}.
 */
@RestController
public class CommunicationsController {

    private final CommunicationsAdmin comms;
    private final RateLimitService rateLimits;
    private final ClientIp clientIp;

    public CommunicationsController(CommunicationsAdmin comms,
            RateLimitService rateLimits, ClientIp clientIp) {
        this.comms = comms;
        this.rateLimits = rateLimits;
        this.clientIp = clientIp;
    }

    private static long actorId() {
        return CurrentUser.require().userId();
    }

    private String ip(HttpServletRequest request) {
        return clientIp.extract(request);
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null
                : value.length() > 512 ? value.substring(0, 512) : value;
    }

    /** {@code limiters.sensitive.check_with_retry(client_ip)}. */
    private void enforceSensitiveLimit(HttpServletRequest request) {
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.SENSITIVE, clientIp.extract(request));
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many requests from this connection. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
    }

    // ------------------------------------------------------------------
    // Campaigns
    // ------------------------------------------------------------------

    @GetMapping("/api/admin/communications/campaigns")
    public CampaignListResponse listCampaigns(
            @RequestParam(required = false) String status,
            @RequestParam(name = "campaign_type", required = false) String campaignType,
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.listCampaigns(status, campaignType, page, pageSize);
    }

    @PostMapping("/api/admin/communications/campaigns")
    public EmailCampaign createCampaign(@RequestBody CampaignInput input,
            HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:compose");
        return comms.createCampaign(actor, input, ip(request), userAgent(request));
    }

    @GetMapping("/api/admin/communications/campaigns/{id}")
    public EmailCampaign getCampaign(@PathVariable long id) {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.getCampaign(id);
    }

    @PutMapping("/api/admin/communications/campaigns/{id}")
    public EmailCampaign updateCampaign(@PathVariable long id,
            @RequestBody CampaignInput input, HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:compose");
        return comms.updateCampaign(actor, id, input, ip(request), userAgent(request));
    }

    @PostMapping("/api/admin/communications/campaigns/{id}/preview")
    public PreviewResponse previewCampaign(@PathVariable long id) {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.previewCampaign(id);
    }

    @PostMapping("/api/admin/communications/campaigns/{id}/test-send")
    public Map<String, String> testSendCampaign(@PathVariable long id,
            @RequestBody TestSendInput input, HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:send");
        comms.testSendCampaign(actor, id, input, ip(request), userAgent(request));
        return Map.of("status", "sent");
    }

    @PostMapping("/api/admin/communications/campaigns/{id}/schedule")
    public EmailCampaign scheduleCampaign(@PathVariable long id,
            @RequestBody ScheduleCampaignInput input, HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:send");
        return comms.scheduleCampaign(actor, id, input, ip(request), userAgent(request));
    }

    @PostMapping("/api/admin/communications/campaigns/{id}/cancel")
    public EmailCampaign cancelCampaign(@PathVariable long id,
            HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:send");
        return comms.cancelCampaign(actor, id, ip(request), userAgent(request));
    }

    @GetMapping("/api/admin/communications/campaigns/{id}/deliveries")
    public DeliveryListResponse listCampaignDeliveries(@PathVariable long id,
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.listCampaignDeliveries(id, page, pageSize);
    }

    @GetMapping("/api/admin/communications/deliveries")
    public DeliveryFeedResponse listDeliveryFeed(
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.listDeliveryFeed(tier, status, page, pageSize);
    }

    // ------------------------------------------------------------------
    // Templates
    // ------------------------------------------------------------------

    @GetMapping("/api/admin/communications/templates")
    public List<EmailTemplate> listTemplates() {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.listTemplates();
    }

    @PostMapping("/api/admin/communications/templates")
    public EmailTemplate createTemplate(@RequestBody TemplateInput input,
            HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:compose");
        return comms.createTemplate(actor, input, ip(request), userAgent(request));
    }

    @PutMapping("/api/admin/communications/templates/{id}")
    public EmailTemplate updateTemplate(@PathVariable long id,
            @RequestBody TemplateInput input, HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:compose");
        return comms.updateTemplate(actor, id, input, ip(request), userAgent(request));
    }

    @PostMapping("/api/admin/communications/templates/{id}/deactivate")
    public Map<String, String> deactivateTemplate(@PathVariable long id,
            HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:manage");
        comms.deactivateTemplate(actor, id, ip(request), userAgent(request));
        return Map.of("status", "deactivated");
    }

    // ------------------------------------------------------------------
    // Audience + suppressions + guest consent
    // ------------------------------------------------------------------

    @GetMapping("/api/admin/communications/audience")
    public AudienceCount audienceCount(@RequestParam String topic) {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.audienceCount(topic);
    }

    @GetMapping("/api/admin/communications/suppressions")
    public SuppressionListResponse listSuppressions(
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        PermissionGateHelper.check(actorId(), "communications:manage");
        return comms.listSuppressions(page, pageSize);
    }

    @PostMapping("/api/admin/communications/suppressions")
    public Map<String, String> addSuppression(@RequestBody SuppressionInput input,
            HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:manage");
        comms.addSuppression(actor, input, ip(request), userAgent(request));
        return Map.of("status", "suppressed");
    }

    @DeleteMapping("/api/admin/communications/suppressions/{email}")
    public Map<String, String> removeSuppression(@PathVariable String email,
            HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:manage");
        comms.removeSuppression(actor, email, ip(request), userAgent(request));
        return Map.of("status", "removed");
    }

    @GetMapping("/api/admin/communications/guests/{guestId}/consent")
    public ConsentStatusResponse guestConsentStatus(@PathVariable long guestId) {
        PermissionGateHelper.check(actorId(), "communications:read");
        return comms.guestConsentStatus(guestId);
    }

    @PostMapping("/api/admin/communications/guests/{guestId}/consent")
    public ConsentStatusResponse recordStaffConsent(@PathVariable long guestId,
            @RequestBody PreferenceUpdateInput input, HttpServletRequest request) {
        long actor = actorId();
        PermissionGateHelper.check(actor, "communications:manage");
        return comms.recordStaffConsent(actor, guestId, input,
                ip(request), userAgent(request));
    }

    // ------------------------------------------------------------------
    // Public: token-authenticated unsubscribe
    // ------------------------------------------------------------------

    @GetMapping("/api/communications/unsubscribe/{token}")
    public PreferencesResponse unsubscribeView(@PathVariable String token,
            HttpServletRequest request) {
        enforceSensitiveLimit(request);
        return comms.unsubscribeView(token);
    }

    @PostMapping("/api/communications/unsubscribe/{token}")
    public PreferencesResponse unsubscribeApply(@PathVariable String token,
            @RequestBody UnsubscribeApplyInput input, HttpServletRequest request) {
        enforceSensitiveLimit(request);
        return comms.unsubscribeApply(token, input, ip(request), userAgent(request));
    }
}
