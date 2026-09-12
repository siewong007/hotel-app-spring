package com.hotelapp.support;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.web.ClientIp;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.portal.PortalAuth;
import com.hotelapp.support.SupportModels.CreateGuestSupportConversationRequest;
import com.hotelapp.support.SupportModels.GuestSupportConversationDetail;
import com.hotelapp.support.SupportModels.GuestSupportConversationListResponse;
import com.hotelapp.support.SupportModels.GuestSupportMessageRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the guest slice of {@code modules/support/routes.rs} +
 * {@code handlers.rs} — {@code /guest-portal/me/support/conversations*}.
 * Mutations carry the IP budget first, then the per-guest budget, mirroring
 * {@code enforce_guest_support_mutation_limit}.
 */
@RestController
public class GuestSupportController {

    private final GuestSupport support;
    private final PortalAuth auth;
    private final RateLimitService rateLimits;
    private final ClientIp clientIp;

    public GuestSupportController(GuestSupport support, PortalAuth auth,
            RateLimitService rateLimits, ClientIp clientIp) {
        this.support = support;
        this.auth = auth;
        this.rateLimits = rateLimits;
        this.clientIp = clientIp;
    }

    private void enforceGuestSupportMutationLimit(long guestId, HttpServletRequest http) {
        RateLimitService.Decision ip = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_SUPPORT_MUTATION_IP,
                clientIp.extract(http));
        if (!ip.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many support requests from this connection. Please try again in "
                            + ip.retryAfterSecs() + " seconds.",
                    ip.retryAfterSecs());
        }
        RateLimitService.Decision guest = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_SUPPORT_MUTATION, "guest:" + guestId);
        if (!guest.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many support messages. Please try again in "
                            + guest.retryAfterSecs() + " seconds.",
                    guest.retryAfterSecs());
        }
    }

    @GetMapping("/api/guest-portal/me/support/conversations")
    public GuestSupportConversationListResponse listGuestConversations(HttpServletRequest http,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long page_size) {
        long guestId = auth.requireGuestSessionForRead(http);
        return support.listGuestConversations(guestId, page, page_size);
    }

    @PostMapping("/api/guest-portal/me/support/conversations")
    public GuestSupportConversationDetail createGuestConversation(HttpServletRequest http,
            @RequestBody CreateGuestSupportConversationRequest request) {
        long guestId = auth.requireGuestSession(http);
        enforceGuestSupportMutationLimit(guestId, http);
        return support.createGuestConversation(guestId, request,
                auth.clientIp(http), auth.userAgent(http));
    }

    @GetMapping("/api/guest-portal/me/support/conversations/{conversationId}")
    public GuestSupportConversationDetail getGuestConversation(HttpServletRequest http,
            @PathVariable long conversationId) {
        long guestId = auth.requireGuestSessionForRead(http);
        return support.getGuestConversation(guestId, conversationId);
    }

    @PostMapping("/api/guest-portal/me/support/conversations/{conversationId}/messages")
    public GuestSupportConversationDetail sendGuestMessage(HttpServletRequest http,
            @PathVariable long conversationId,
            @RequestBody GuestSupportMessageRequest request) {
        long guestId = auth.requireGuestSession(http);
        enforceGuestSupportMutationLimit(guestId, http);
        return support.sendGuestMessage(guestId, conversationId, request,
                auth.clientIp(http), auth.userAgent(http));
    }

    @PostMapping("/api/guest-portal/me/support/conversations/{conversationId}/reopen")
    public GuestSupportConversationDetail reopenGuestConversation(HttpServletRequest http,
            @PathVariable long conversationId) {
        long guestId = auth.requireGuestSession(http);
        enforceGuestSupportMutationLimit(guestId, http);
        return support.reopenGuestConversation(guestId, conversationId,
                auth.clientIp(http), auth.userAgent(http));
    }
}
