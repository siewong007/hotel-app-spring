package com.hotelapp.portal;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.portal.PortalModels.GuestBookingCancellationRequest;
import com.hotelapp.portal.PortalModels.GuestBookingPaymentRequest;
import com.hotelapp.portal.PortalModels.GuestPortalBookingResponse;
import com.hotelapp.portal.PortalModels.GuestPortalClaimAccountRequest;
import com.hotelapp.portal.PortalModels.GuestPortalClaimAccountResponse;
import com.hotelapp.portal.PortalModels.GuestPortalLoginResponse;
import com.hotelapp.portal.PortalModels.GuestPortalMeResponse;
import com.hotelapp.portal.PortalModels.GuestPortalPage;
import com.hotelapp.portal.PortalModels.GuestPortalProfileUpdate;
import com.hotelapp.portal.PortalModels.GuestPortalVerifyRequest;
import com.hotelapp.portal.PortalModels.GuestPortalVerifyResponse;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import com.hotelapp.portal.PortalModels.PaypalCaptureRequest;
import com.hotelapp.portal.PortalModels.PaypalCreateOrderResponse;
import com.hotelapp.portal.PortalModels.PreCheckInUpdateRequest;
import com.hotelapp.portal.PortalModels.SessionPaypalCaptureRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Port of {@code routes/guest_portal.rs} + {@code handlers/guest_portal.rs} —
 * the guest self-service portal surface: verify, token-authenticated booking
 * reads/writes, session login/logout, the {@code me} hub and guest payments.
 */
@RestController
public class PortalController {

    private final PortalService service;
    private final PortalAuth auth;
    private final PortalPayments payments;
    private final RateLimitService rateLimits;

    public PortalController(PortalService service, PortalAuth auth, PortalPayments payments,
            RateLimitService rateLimits) {
        this.service = service;
        this.auth = auth;
        this.payments = payments;
        this.rateLimits = rateLimits;
    }

    // ------------------------------------------------------------------
    // Verification + booking access token surface
    // ------------------------------------------------------------------

    /** POST /guest-portal/verify — IP limit, length check, keyed booking limit. */
    @PostMapping("/api/guest-portal/verify")
    public GuestPortalVerifyResponse verify(HttpServletRequest http,
            @RequestBody GuestPortalVerifyRequest body) {
        String ip = auth.clientIp(http);
        RateLimitService.Decision verifyDecision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_VERIFY, ip);
        if (!verifyDecision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many guest portal verification attempts. Please try again in "
                            + verifyDecision.retryAfterSecs() + " seconds.",
                    verifyDecision.retryAfterSecs());
        }
        if (body.bookingNumber() != null && body.bookingNumber().length() > 50) {
            throw ApiError.badRequest("Invalid booking number.");
        }
        String bookingKey = body.bookingNumber() == null
                ? "<empty>" : body.bookingNumber().trim().toUpperCase();
        if (bookingKey.isEmpty()) {
            bookingKey = "<empty>";
        }
        RateLimitService.Decision bookingDecision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_BOOKING, bookingKey);
        if (!bookingDecision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many attempts for this booking. Please try again in "
                            + bookingDecision.retryAfterSecs() + " seconds.",
                    bookingDecision.retryAfterSecs());
        }
        return service.verifyGuestBooking(body);
    }

    @GetMapping("/api/guest-portal/booking")
    public GuestPortalBookingResponse bookingHeader(HttpServletRequest http) {
        String token = auth.requireBookingTokenForRead(http, null);
        return service.getBookingByToken(token);
    }

    @GetMapping("/api/guest-portal/booking/{token}")
    public GuestPortalBookingResponse bookingPath(HttpServletRequest http,
            @PathVariable String token) {
        return service.getBookingByToken(auth.requireBookingTokenForRead(http, token));
    }

    @PostMapping("/api/guest-portal/pre-checkin")
    public GuestPortalBookingResponse preCheckinHeader(HttpServletRequest http,
            @RequestBody PreCheckInUpdateRequest body) {
        String token = auth.requireBookingTokenForWrite(http, null,
                "Too many pre-check-in attempts for this booking. Please try again in");
        return service.submitPrecheckinUpdate(token, body);
    }

    @PostMapping("/api/guest-portal/pre-checkin/{token}")
    public GuestPortalBookingResponse preCheckinPath(HttpServletRequest http,
            @PathVariable String token, @RequestBody PreCheckInUpdateRequest body) {
        return service.submitPrecheckinUpdate(
                auth.requireBookingTokenForWrite(http, token,
                        "Too many pre-check-in attempts for this booking. Please try again in"),
                body);
    }

    @PostMapping("/api/guest-portal/auto-checkin")
    public PortalModels.AutoCheckinResponse autoCheckinHeader(HttpServletRequest http) {
        String token = auth.requireBookingTokenForWrite(http, null,
                "Too many check-in attempts for this booking. Please try again in");
        return service.autoCheckinByToken(token);
    }

    @PostMapping("/api/guest-portal/auto-checkin/{token}")
    public PortalModels.AutoCheckinResponse autoCheckinPath(HttpServletRequest http,
            @PathVariable String token) {
        return service.autoCheckinByToken(auth.requireBookingTokenForWrite(http, token,
                "Too many check-in attempts for this booking. Please try again in"));
    }

    /**
     * POST /guest-portal/claim-account — token in X-Booking-Access-Token only;
     * the body carries a password so the token is never accepted via the URL.
     * Carries the registration IP budget on top of the token write budget.
     */
    @PostMapping("/api/guest-portal/claim-account")
    public GuestPortalClaimAccountResponse claimAccount(HttpServletRequest http,
            @RequestBody GuestPortalClaimAccountRequest body) {
        String ip = auth.clientIp(http);
        RateLimitService.Decision registerDecision = rateLimits.check(
                RateLimitService.Category.REGISTER, ip);
        if (!registerDecision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many registration attempts. Please try again in "
                            + registerDecision.retryAfterSecs() + " seconds.",
                    registerDecision.retryAfterSecs());
        }
        String token = auth.requireBookingTokenForWrite(http, null,
                "Too many account attempts for this booking. Please try again in");
        return service.claimBookingAccount(token, body, http);
    }

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    /** POST /guest-portal/session — requires the normal account JWT. */
    @PostMapping("/api/guest-portal/session")
    public GuestPortalLoginResponse createSession(HttpServletRequest http) {
        long userId = CurrentUser.require().userId();
        return service.createAuthenticatedGuestPortalSession(userId,
                auth.clientIp(http), auth.userAgent(http));
    }

    /** POST /guest-portal/logout — revoke the bearer session. */
    @PostMapping("/api/guest-portal/logout")
    public void logout(HttpServletRequest http) {
        service.logoutGuestSession(PortalAuth.bearerToken(http));
    }

    // ------------------------------------------------------------------
    // Me hub — session-authenticated guest-scoped reads
    // ------------------------------------------------------------------

    @GetMapping("/api/guest-portal/me")
    public GuestPortalMeResponse me(HttpServletRequest http) {
        return service.getMe(auth.requireGuestSessionForRead(http));
    }

    @PatchMapping("/api/guest-portal/me/profile")
    public GuestPortalMeResponse updateProfile(HttpServletRequest http,
            @RequestBody GuestPortalProfileUpdate body) {
        return service.updateMyProfile(auth.requireGuestSessionForRead(http), body);
    }

    @GetMapping("/api/guest-portal/me/bookings")
    public GuestPortalPage<PortalModels.GuestPortalBookingSummary> myBookings(
            HttpServletRequest http, @RequestParam Map<String, String> query) {
        long guestId = auth.requireGuestSessionForRead(http);
        long[] limitOffset = pageLimitOffset(query);
        return service.getMyBookings(guestId, limitOffset[0], limitOffset[1],
                query.get("search"));
    }

    @PostMapping("/api/guest-portal/me/bookings/{id}/cancel")
    public Map<String, Object> cancelBooking(HttpServletRequest http, @PathVariable long id,
            @RequestBody(required = false) GuestBookingCancellationRequest body) {
        return service.cancelMyBooking(auth.requireGuestSessionForRead(http), id, body);
    }

    @GetMapping("/api/guest-portal/me/transactions")
    public GuestPortalPage<PortalModels.GuestPortalTransaction> myTransactions(
            HttpServletRequest http, @RequestParam Map<String, String> query) {
        long guestId = auth.requireGuestSessionForRead(http);
        long[] limitOffset = pageLimitOffset(query);
        return service.getMyTransactions(guestId, limitOffset[0], limitOffset[1]);
    }

    @GetMapping("/api/guest-portal/me/membership")
    public PortalModels.GuestPortalMembershipResponse myMembership(HttpServletRequest http) {
        return service.getMyMembership(auth.requireGuestSessionForRead(http));
    }

    @GetMapping("/api/guest-portal/me/benefits")
    public PortalModels.GuestPortalBenefitsResponse myBenefits(HttpServletRequest http) {
        return service.getMyBenefits(auth.requireGuestSessionForRead(http));
    }

    @GetMapping("/api/guest-portal/me/credits")
    public PortalModels.GuestPortalCreditsResponse myCredits(HttpServletRequest http) {
        return service.getMyCredits(auth.requireGuestSessionForRead(http));
    }

    // ------------------------------------------------------------------
    // Payment config + payments
    // ------------------------------------------------------------------

    /**
     * GET /guest-portal/payment-config — booking header wins over the session
     * bearer; an unauthenticated caller gets 401.
     */
    @GetMapping("/api/guest-portal/payment-config")
    public PortalModels.GuestPaymentConfig paymentConfig(HttpServletRequest http) {
        if (http.getHeader(PortalAuth.BOOKING_ACCESS_TOKEN_HEADER) != null) {
            String token = auth.requireBookingTokenForRead(http, null);
            service.getBookingByToken(token);
        } else {
            auth.requireGuestSessionForRead(http);
        }
        return payments.guestPaymentConfig();
    }

    @PostMapping("/api/guest-portal/me/payments/bank-transfer")
    public PaymentActionResponse sessionBankTransfer(HttpServletRequest http,
            @RequestBody GuestBookingPaymentRequest body) {
        long guestId = auth.requireGuestSession(http);
        auth.checkGuestPaymentRateLimit(guestId);
        return service.sessionBankTransfer(guestId, body.bookingId());
    }

    @PostMapping("/api/guest-portal/me/payments/{paymentId}/receipt")
    public Map<String, Object> sessionUploadReceipt(HttpServletRequest http,
            @PathVariable long paymentId, @RequestParam("file") MultipartFile file)
            throws java.io.IOException {
        long guestId = auth.requireGuestSession(http);
        service.sessionUploadPaymentReceipt(guestId, paymentId, file.getBytes());
        return Map.of("uploaded", true);
    }

    @PostMapping("/api/guest-portal/me/payments/paypal/create-order")
    public PaypalCreateOrderResponse sessionPaypalOrder(HttpServletRequest http,
            @RequestBody GuestBookingPaymentRequest body) {
        long guestId = auth.requireGuestSession(http);
        auth.checkGuestPaymentRateLimit(guestId);
        return service.sessionCreatePaypalOrder(guestId, body.bookingId());
    }

    @PostMapping("/api/guest-portal/me/payments/paypal/capture")
    public PaymentActionResponse sessionPaypalCapture(HttpServletRequest http,
            @RequestBody SessionPaypalCaptureRequest body) {
        long guestId = auth.requireGuestSession(http);
        auth.checkGuestPaymentRateLimit(guestId);
        return service.sessionCapturePaypal(guestId, body);
    }

    // Token payment routes — header variants first.

    @PostMapping("/api/guest-portal/booking/payments/bank-transfer")
    public PaymentActionResponse tokenBankTransferHeader(HttpServletRequest http) {
        return service.tokenBankTransfer(auth.requirePaymentBookingToken(http, null));
    }

    @PostMapping("/api/guest-portal/booking/payments/{paymentId}/receipt")
    public Map<String, Object> tokenUploadReceiptHeader(HttpServletRequest http,
            @PathVariable long paymentId, @RequestParam("file") MultipartFile file)
            throws java.io.IOException {
        String token = auth.requirePaymentBookingToken(http, null);
        service.tokenUploadPaymentReceipt(token, paymentId, file.getBytes());
        return Map.of("uploaded", true);
    }

    @PostMapping("/api/guest-portal/booking/payments/paypal/create-order")
    public PaypalCreateOrderResponse tokenPaypalOrderHeader(HttpServletRequest http) {
        return service.tokenCreatePaypalOrder(auth.requirePaymentBookingToken(http, null));
    }

    @PostMapping("/api/guest-portal/booking/payments/paypal/capture")
    public PaymentActionResponse tokenPaypalCaptureHeader(HttpServletRequest http,
            @RequestBody PaypalCaptureRequest body) {
        return service.tokenCapturePaypal(auth.requirePaymentBookingToken(http, null), body);
    }

    @PostMapping("/api/guest-portal/booking/{token}/payments/bank-transfer")
    public PaymentActionResponse tokenBankTransfer(HttpServletRequest http,
            @PathVariable String token) {
        return service.tokenBankTransfer(auth.requirePaymentBookingToken(http, token));
    }

    @PostMapping("/api/guest-portal/booking/{token}/payments/{paymentId}/receipt")
    public Map<String, Object> tokenUploadReceipt(HttpServletRequest http,
            @PathVariable String token, @PathVariable long paymentId,
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        service.tokenUploadPaymentReceipt(
                auth.requirePaymentBookingToken(http, token), paymentId, file.getBytes());
        return Map.of("uploaded", true);
    }

    @PostMapping("/api/guest-portal/booking/{token}/payments/paypal/create-order")
    public PaypalCreateOrderResponse tokenPaypalOrder(HttpServletRequest http,
            @PathVariable String token) {
        return service.tokenCreatePaypalOrder(auth.requirePaymentBookingToken(http, token));
    }

    @PostMapping("/api/guest-portal/booking/{token}/payments/paypal/capture")
    public PaymentActionResponse tokenPaypalCapture(HttpServletRequest http,
            @PathVariable String token, @RequestBody PaypalCaptureRequest body) {
        return service.tokenCapturePaypal(auth.requirePaymentBookingToken(http, token), body);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** {@code GuestPortalPageQuery::limit_offset} — page/per_page clamps. */
    private static long[] pageLimitOffset(Map<String, String> query) {
        long perPage = parseLong(query.get("per_page"), 20);
        perPage = Math.max(1, Math.min(100, perPage));
        long page = Math.max(1, parseLong(query.get("page"), 1));
        return new long[] {perPage, (page - 1) * perPage};
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
