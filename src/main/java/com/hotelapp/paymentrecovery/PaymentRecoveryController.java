package com.hotelapp.paymentrecovery;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.paymentrecovery.PaymentRecoveryModels.CaptureRecoveredPaypalRequest;
import com.hotelapp.paymentrecovery.PaymentRecoveryModels.PaymentRecoveryView;
import com.hotelapp.portal.PortalAuth;
import com.hotelapp.portal.PortalModels.PaymentActionResponse;
import com.hotelapp.portal.PortalModels.PaypalCreateOrderResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code routes/payment_retry.rs} — public payment-recovery endpoints reached
 * only by an emailed capability. No session, no account, no booking-access
 * token: the capability in the URL is the entire authority, and it authorises
 * exactly one thing — replacing one rejected payment on one booking.
 *
 * Viewing is deliberately side-effect free: mail clients and security
 * appliances follow links in messages they scan, so a view that consumed the
 * capability would hand the guest a dead link.
 */
@RestController
public class PaymentRecoveryController {

    private final PaymentRecovery recovery;
    private final PortalAuth auth;
    private final RateLimitService rateLimits;

    public PaymentRecoveryController(PaymentRecovery recovery, PortalAuth auth,
            RateLimitService rateLimits) {
        this.recovery = recovery;
        this.auth = auth;
        this.rateLimits = rateLimits;
    }

    /**
     * Shared ceiling for unauthenticated token-gated requests from one IP —
     * bounds a flood of distinct garbage tokens, each of which would otherwise
     * cost a hash and a lookup. ({@code require_capacity} upstream.)
     */
    private void requireCapacity(HttpServletRequest request) {
        String ip = auth.clientIp(request);
        RateLimitService.Decision decision = rateLimits.check(
                RateLimitService.Category.GUEST_PORTAL_TOKEN_IP, ip);
        if (!decision.allowed()) {
            throw ApiError.tooManyRequestsRetryAfter(
                    "Too many payment attempts from this connection. "
                            + "Please try again in " + decision.retryAfterSecs() + " seconds.",
                    decision.retryAfterSecs());
        }
    }

    @GetMapping("/api/booking/recover-payment/{token}")
    public PaymentRecoveryView viewRecovery(HttpServletRequest http,
            @PathVariable String token) {
        requireCapacity(http);
        return recovery.describeRecovery(token);
    }

    @PostMapping("/api/booking/recover-payment/{token}/bank-transfer")
    public PaymentActionResponse recoverBankTransfer(HttpServletRequest http,
            @PathVariable String token) {
        requireCapacity(http);
        return recovery.recoverWithBankTransfer(token);
    }

    @PostMapping("/api/booking/recover-payment/{token}/paypal/create-order")
    public PaypalCreateOrderResponse recoverPaypalCreateOrder(HttpServletRequest http,
            @PathVariable String token) {
        requireCapacity(http);
        return recovery.recoverWithPaypal(token);
    }

    // Capture stays reachable after the capability is spent: the guest has to
    // approve the order in PayPal's window and return, and refusing a consumed
    // link here would strand every authorised order.
    @PostMapping("/api/booking/recover-payment/{token}/paypal/capture")
    public PaymentActionResponse recoverPaypalCapture(HttpServletRequest http,
            @PathVariable String token, @RequestBody CaptureRecoveredPaypalRequest body) {
        requireCapacity(http);
        return recovery.captureRecoveredPaypal(token, body.orderId(), body.paymentId());
    }

    /**
     * Attach payment evidence to the claim raised through this link — scoped
     * to exactly the payment the capability produced.
     */
    @PostMapping("/api/booking/recover-payment/{token}/payments/{paymentId}/receipt")
    public Map<String, Object> recoverUploadReceipt(HttpServletRequest http,
            @PathVariable String token, @PathVariable long paymentId,
            @RequestParam(value = "file", required = false) MultipartFile file)
            throws IOException {
        requireCapacity(http);
        if (file == null) {
            // upstream receipt_upload_bytes: absent "file" part
            throw ApiError.badRequest("Select a receipt file to upload.");
        }
        recovery.uploadRecoveredReceipt(token, paymentId, file.getBytes());
        return Map.of("uploaded", true);
    }
}
