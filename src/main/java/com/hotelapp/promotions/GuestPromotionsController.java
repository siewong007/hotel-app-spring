package com.hotelapp.promotions;

import com.hotelapp.portal.PortalAuth;
import com.hotelapp.promotions.PromotionModels.ClaimPromotionInput;
import com.hotelapp.promotions.PromotionModels.GuestPromotionListResponse;
import com.hotelapp.promotions.PromotionModels.Voucher;
import com.hotelapp.promotions.PromotionModels.VoucherListResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the guest slice of {@code modules/promotions/routes.rs} —
 * {@code /guest-portal/me/promotions*}, {@code /guest-portal/me/vouchers}.
 * Reads ride the shared session read budget; the claim is a plain session call.
 */
@RestController
public class GuestPromotionsController {

    private final GuestPromotions promotions;
    private final GuestPromotionsTx promotionsTx;
    private final PortalAuth auth;

    public GuestPromotionsController(GuestPromotions promotions,
            GuestPromotionsTx promotionsTx, PortalAuth auth) {
        this.promotions = promotions;
        this.promotionsTx = promotionsTx;
        this.auth = auth;
    }

    @GetMapping("/api/guest-portal/me/promotions")
    public GuestPromotionListResponse listGuestPromotions(HttpServletRequest http,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long page_size) {
        long guestId = auth.requireGuestSessionForRead(http);
        return promotions.listGuestPromotions(guestId, page, page_size);
    }

    @PostMapping("/api/guest-portal/me/promotions/{promotionId}/claim")
    public Voucher claimGuestPromotion(HttpServletRequest http,
            @PathVariable long promotionId, @RequestBody ClaimPromotionInput input) {
        long guestId = auth.requireGuestSession(http);
        return promotions.claimGuestPromotion(promotionsTx, guestId, promotionId,
                input == null ? null : input.clientRequestId(),
                auth.clientIp(http), auth.userAgent(http));
    }

    @GetMapping("/api/guest-portal/me/vouchers")
    public VoucherListResponse listGuestVouchers(HttpServletRequest http,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long page_size) {
        long guestId = auth.requireGuestSessionForRead(http);
        return promotions.listGuestVouchers(guestId, page, page_size);
    }
}
