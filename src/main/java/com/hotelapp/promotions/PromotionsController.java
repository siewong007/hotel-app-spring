package com.hotelapp.promotions;

import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.core.web.ClientIp;
import com.hotelapp.promotions.PromotionModels.Promotion;
import com.hotelapp.promotions.PromotionModels.PromotionActionInput;
import com.hotelapp.promotions.PromotionModels.PromotionInput;
import com.hotelapp.promotions.PromotionModels.PromotionListResponse;
import com.hotelapp.promotions.PromotionModels.PublicPromotion;
import com.hotelapp.promotions.PromotionModels.PublicPromotionListResponse;
import com.hotelapp.promotions.PromotionModels.Voucher;
import com.hotelapp.promotions.PromotionModels.VoucherIssueInput;
import com.hotelapp.promotions.PromotionModels.VoucherListResponse;
import com.hotelapp.promotions.PromotionModels.VoucherRevokeInput;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for {@code modules/promotions/routes.rs}: the public promotion
 * catalogue ({@code /api/promotions}) plus the staff
 * {@code /api/admin/promotions*} and {@code /api/admin/vouchers*} surfaces.
 * Guest-portal promotion/voucher routes live in
 * {@link GuestPromotionsController}.
 */
@RestController
public class PromotionsController {

    private final GuestPromotions publicCatalog;
    private final AdminPromotions admin;
    private final AdminPromotionsTx adminTx;
    private final ClientIp clientIp;

    public PromotionsController(GuestPromotions publicCatalog, AdminPromotions admin,
            AdminPromotionsTx adminTx, ClientIp clientIp) {
        this.publicCatalog = publicCatalog;
        this.admin = admin;
        this.adminTx = adminTx;
        this.clientIp = clientIp;
    }

    private static long actorId() {
        return CurrentUser.require().userId();
    }

    private static void gate(long userId, String permission) {
        PermissionGateHelper.check(userId, permission);
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        return value == null ? null
                : value.length() > 512 ? value.substring(0, 512) : value;
    }

    // ------------------------------------------------------------------
    // Public catalogue — no authentication, like upstream
    // ------------------------------------------------------------------

    @GetMapping("/api/promotions")
    public PublicPromotionListResponse listPublicPromotions(
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        return publicCatalog.listPublicPromotions(page, pageSize);
    }

    @GetMapping("/api/promotions/{slug}")
    public PublicPromotion getPublicPromotion(@PathVariable String slug) {
        return publicCatalog.getPublicPromotion(slug);
    }

    // ------------------------------------------------------------------
    // Staff promotions
    // ------------------------------------------------------------------

    @GetMapping("/api/admin/promotions")
    public PromotionListResponse listAdminPromotions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        gate(actorId(), "promotions:read");
        return admin.listAdminPromotions(status, search, page, pageSize);
    }

    @PostMapping("/api/admin/promotions")
    public Promotion createAdminPromotion(HttpServletRequest http,
            @RequestBody PromotionInput input) {
        long actor = actorId();
        gate(actor, "promotions:manage");
        return admin.createAdminPromotion(adminTx, actor, input,
                clientIp.extract(http), userAgent(http));
    }

    @GetMapping("/api/admin/promotions/{promotionId}")
    public Promotion getAdminPromotion(@PathVariable long promotionId) {
        gate(actorId(), "promotions:read");
        return admin.updatedPromotion(promotionId);
    }

    @PutMapping("/api/admin/promotions/{promotionId}")
    public Promotion updateAdminPromotion(HttpServletRequest http,
            @PathVariable long promotionId, @RequestBody PromotionInput input) {
        long actor = actorId();
        gate(actor, "promotions:manage");
        return admin.updateAdminPromotion(adminTx, actor, promotionId, input,
                clientIp.extract(http), userAgent(http));
    }

    @PostMapping("/api/admin/promotions/{promotionId}/publish")
    public Promotion publishAdminPromotion(HttpServletRequest http,
            @PathVariable long promotionId, @RequestBody PromotionActionInput input) {
        long actor = actorId();
        gate(actor, "promotions:manage");
        return admin.transitionAdminPromotion(adminTx, actor, promotionId, "published",
                input == null ? null : input.expectedVersion(),
                clientIp.extract(http), userAgent(http));
    }

    @PostMapping("/api/admin/promotions/{promotionId}/pause")
    public Promotion pauseAdminPromotion(HttpServletRequest http,
            @PathVariable long promotionId, @RequestBody PromotionActionInput input) {
        long actor = actorId();
        gate(actor, "promotions:manage");
        return admin.transitionAdminPromotion(adminTx, actor, promotionId, "paused",
                input == null ? null : input.expectedVersion(),
                clientIp.extract(http), userAgent(http));
    }

    @PostMapping("/api/admin/promotions/{promotionId}/archive")
    public Promotion archiveAdminPromotion(HttpServletRequest http,
            @PathVariable long promotionId, @RequestBody PromotionActionInput input) {
        long actor = actorId();
        gate(actor, "promotions:manage");
        return admin.transitionAdminPromotion(adminTx, actor, promotionId, "archived",
                input == null ? null : input.expectedVersion(),
                clientIp.extract(http), userAgent(http));
    }

    // ------------------------------------------------------------------
    // Staff vouchers
    // ------------------------------------------------------------------

    @GetMapping("/api/admin/vouchers")
    public VoucherListResponse listAdminVouchers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long page,
            @RequestParam(name = "page_size", required = false) Long pageSize) {
        gate(actorId(), "vouchers:read");
        return admin.listAdminVouchers(status, search, page, pageSize);
    }

    @PostMapping("/api/admin/vouchers")
    public Voucher issueAdminVoucher(HttpServletRequest http,
            @RequestBody VoucherIssueInput input) {
        long actor = actorId();
        gate(actor, "vouchers:manage");
        return admin.issueAdminVoucher(adminTx, actor, input,
                clientIp.extract(http), userAgent(http));
    }

    @PostMapping("/api/admin/vouchers/{voucherId}/revoke")
    public Voucher revokeAdminVoucher(HttpServletRequest http,
            @PathVariable long voucherId, @RequestBody VoucherRevokeInput input) {
        long actor = actorId();
        gate(actor, "vouchers:manage");
        return admin.revokeAdminVoucher(adminTx, actor, voucherId, input,
                clientIp.extract(http), userAgent(http));
    }
}
