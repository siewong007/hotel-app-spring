package com.hotelapp.guestbooking;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.guestbooking.FunnelModels.AnonymousBookingRequest;
import com.hotelapp.guestbooking.FunnelModels.BookingQuoteRequest;
import com.hotelapp.guestbooking.FunnelModels.CreateGuestBookingRequest;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingConfirmation;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingOffer;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingQuote;
import com.hotelapp.guestbooking.FunnelModels.GuestBookingVoucherOptions;
import com.hotelapp.guestbooking.FunnelModels.OnlineInventoryAllocation;
import com.hotelapp.guestbooking.FunnelModels.UpdateOnlineInventoryRequest;
import com.hotelapp.portal.PortalAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transport for {@code modules/guest_booking}: public offers/quote/reservations,
 * the session-authenticated booking options/quotes/create, and the
 * online-inventory admin surface. (The availability WebSocket arrives in 4f.)
 */
@RestController
public class BookingFunnelController {

    private final FunnelService funnel;
    private final PortalAuth auth;
    private final RateLimitService rateLimits;

    public BookingFunnelController(FunnelService funnel, PortalAuth auth,
            RateLimitService rateLimits) {
        this.funnel = funnel;
        this.auth = auth;
        this.rateLimits = rateLimits;
    }

    /** Upstream require_read_capacity — guest-keyed read budget. */
    private void requireReadCapacity(long guestId) {
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_TOKEN_READ, "guest:" + guestId);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many portal requests. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
    }

    /** Upstream require_public_capacity — IP-keyed, the only identity an anonymous caller has. */
    private void requirePublicCapacity(RateLimitService.Category category,
            HttpServletRequest request, String what) {
        RateLimitService.Decision decision = rateLimits.check(category,
                auth.clientIp(request));
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many " + what + " from this connection. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
    }

    // ------------------------------------------------------------------
    // Public funnel (no account, no token — list prices only)
    // ------------------------------------------------------------------

    @GetMapping("/api/booking/offers")
    public List<GuestBookingOffer> publicOffers(@RequestParam("check_in_date") String checkIn,
            @RequestParam("check_out_date") String checkOut,
            @RequestParam(required = false) Integer adults,
            @RequestParam(required = false) Integer children,
            HttpServletRequest request) {
        requirePublicCapacity(RateLimitService.Category.PUBLIC_BOOKING_READ_IP, request,
                "booking searches");
        return funnel.search(null, checkIn, checkOut, adults, children);
    }

    @PostMapping("/api/booking/quote")
    public GuestBookingQuote publicQuote(@RequestBody BookingQuoteRequest body,
            HttpServletRequest request) {
        requirePublicCapacity(RateLimitService.Category.PUBLIC_BOOKING_READ_IP, request,
                "booking quotes");
        return funnel.quote(null, body.asPublic());
    }

    @PostMapping("/api/booking/reservations")
    public GuestBookingConfirmation publicReservation(
            @RequestBody AnonymousBookingRequest body, HttpServletRequest request) {
        requirePublicCapacity(RateLimitService.Category.PUBLIC_BOOKING_CREATE_IP, request,
                "booking attempts");
        return funnel.createAnonymous(body, auth.clientIp(request), auth.userAgent(request));
    }

    // ------------------------------------------------------------------
    // Session-authenticated funnel
    // ------------------------------------------------------------------

    @GetMapping("/api/guest-portal/me/booking-options")
    public List<GuestBookingOffer> bookingOptions(@RequestParam("check_in_date") String checkIn,
            @RequestParam("check_out_date") String checkOut,
            @RequestParam(required = false) Integer adults,
            @RequestParam(required = false) Integer children,
            HttpServletRequest request) {
        long guestId = auth.requireGuestSession(request);
        requireReadCapacity(guestId);
        return funnel.search(guestId, checkIn, checkOut, adults, children);
    }

    @PostMapping("/api/guest-portal/me/booking-quote")
    public GuestBookingQuote bookingQuote(@RequestBody BookingQuoteRequest body,
            HttpServletRequest request) {
        long guestId = auth.requireGuestSession(request);
        requireReadCapacity(guestId);
        return funnel.quote(guestId, body);
    }

    @PostMapping("/api/guest-portal/me/booking-voucher-options")
    public GuestBookingVoucherOptions bookingVoucherOptions(
            @RequestBody BookingQuoteRequest body, HttpServletRequest request) {
        long guestId = auth.requireGuestSession(request);
        requireReadCapacity(guestId);
        return funnel.quoteWithEligibleVouchers(guestId, body);
    }

    @PostMapping("/api/guest-portal/me/bookings")
    public GuestBookingConfirmation createBooking(@RequestBody CreateGuestBookingRequest body,
            HttpServletRequest request) {
        long guestId = auth.requireGuestSession(request);
        String ip = auth.clientIp(request);
        RateLimitService.Decision ipDecision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_BOOKING_CREATE_IP, ip);
        if (!ipDecision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many booking attempts from this connection. Please try again in "
                            + ipDecision.retryAfterSecs() + " seconds.",
                    ipDecision.retryAfterSecs());
        }
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_BOOKING_CREATE, "guest:" + guestId);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many booking attempts. Please try again in "
                            + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
        return funnel.create(guestId, body, ip, auth.userAgent(request));
    }

    // ------------------------------------------------------------------
    // Online inventory admin (rooms:update)
    // ------------------------------------------------------------------

    @GetMapping("/api/admin/online-inventory")
    public List<OnlineInventoryAllocation> listOnlineInventory(
            @RequestParam("stay_date") String stayDate) {
        PermissionGateHelper.checkAny(CurrentUser.require().userId(),
                List.of("rooms:update", "rooms:manage"));
        return funnel.listOnlineInventory(stayDate);
    }

    @PutMapping("/api/admin/online-inventory/{roomTypeId}/{stayDate}")
    public OnlineInventoryAllocation updateOnlineInventory(@PathVariable long roomTypeId,
            @PathVariable String stayDate, @RequestBody UpdateOnlineInventoryRequest body) {
        long actorId = CurrentUser.require().userId();
        PermissionGateHelper.checkAny(actorId, List.of("rooms:update", "rooms:manage"));
        return funnel.updateOnlineInventory(roomTypeId, stayDate, body, actorId);
    }
}
