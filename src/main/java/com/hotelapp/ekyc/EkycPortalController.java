package com.hotelapp.ekyc;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.ekyc.EkycModels.EkycStatusResponse;
import com.hotelapp.ekyc.EkycModels.EkycSubmissionRequest;
import com.hotelapp.portal.PortalAuth;
import com.hotelapp.portal.PortalBookingOps;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Transport for {@code modules/ekyc/portal.rs} — self-service identity
 * verification behind the guest portal bearer session. The session's guest_id
 * is bridged to the users.id the eKYC domain is keyed on via
 * {@code find_guest_user_id}; a deactivated account resolves to Forbidden.
 */
@RestController
public class EkycPortalController {

    private final EkycPortalService service;
    private final PortalAuth auth;
    private final PortalBookingOps bookingOps;
    private final RateLimitService rateLimits;
    private final AuditWriter audit;

    public EkycPortalController(EkycPortalService service, PortalAuth auth,
            PortalBookingOps bookingOps, RateLimitService rateLimits,
            AuditWriter audit) {
        this.service = service;
        this.auth = auth;
        this.bookingOps = bookingOps;
        this.rateLimits = rateLimits;
        this.audit = audit;
    }

    /** {@code resolve_portal_user_id} — never provisions a user row. */
    private long resolvePortalUserId(long guestId) {
        Long userId = bookingOps.findGuestUserId(guestId);
        if (userId == null) {
            throw ApiError.forbidden(
                    "This portal account is no longer active. Please contact the front desk.");
        }
        return userId;
    }

    /** {@code enforce_ekyc_write_limit} — IP first, then per-guest. */
    private void enforceEkycWriteLimit(long guestId, HttpServletRequest request) {
        RateLimitService.Decision ip = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_EKYC_IP, auth.clientIp(request));
        if (!ip.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many verification requests from this connection. Please try again in "
                            + ip.retryAfterSecs() + " seconds.", ip.retryAfterSecs());
        }
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_EKYC, "guest:" + guestId);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many verification attempts. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null
                : value.length() > 512 ? value.substring(0, 512) : value;
    }

    /** GET /api/guest-portal/me/ekyc — null body when never submitted. */
    @GetMapping("/api/guest-portal/me/ekyc")
    public Optional<EkycStatusResponse> getStatus(HttpServletRequest request) {
        long guestId = auth.requireGuestSessionForRead(request);
        return Optional.ofNullable(service.getStatus(resolvePortalUserId(guestId)));
    }

    /** POST /api/guest-portal/me/ekyc/documents — one image under the caller's prefix. */
    @PostMapping("/api/guest-portal/me/ekyc/documents")
    public Map<String, Object> uploadDocument(HttpServletRequest request,
            @RequestParam(value = "documentType", required = false) String documentTypeCamel,
            @RequestParam(value = "document_type", required = false) String documentTypeSnake,
            @RequestParam(value = "file", required = false) MultipartFile file)
            throws IOException {
        long guestId = auth.requireGuestSession(request);
        enforceEkycWriteLimit(guestId, request);
        long userId = resolvePortalUserId(guestId);
        if (file == null) {
            throw ApiError.badRequest("No file uploaded");
        }
        String documentType = documentTypeSnake != null ? documentTypeSnake : documentTypeCamel;
        Map<String, Object> result = service.storeDocumentUpload(
                userId, documentType, file.getContentType(), file.getBytes());
        audit.event(userId, "ekyc_document_uploaded", "guest", guestId,
                Map.of("channel", "guest_portal", "guest_id", guestId,
                        "document_type", result.get("document_type"),
                        "stored_path", result.get("file_path")),
                auth.clientIp(request), userAgent(request));
        return result;
    }

    /** POST /api/guest-portal/me/ekyc/submit — GuestPortal channel, stored paths only. */
    @PostMapping("/api/guest-portal/me/ekyc/submit")
    public EkycStatusResponse submit(HttpServletRequest request,
            @RequestBody EkycSubmissionRequest body) {
        long guestId = auth.requireGuestSession(request);
        enforceEkycWriteLimit(guestId, request);
        long userId = resolvePortalUserId(guestId);
        return service.submit(userId, body, auth.clientIp(request), userAgent(request));
    }
}
