package com.hotelapp.communications;

import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.error.ApiError;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Stateless HMAC-signed unsubscribe tokens, mirroring
 * {@code modules/communications/tokens.rs}.
 *
 * <p>Format: {@code base64url(guest_id) + "." + hex(HMAC-SHA256(JWT_SECRET,
 * guest_id))}. Tokens carry no expiry: unsubscribe links in already-delivered
 * email must keep working indefinitely.
 */
@Component
public class UnsubscribeTokens {

    private final AppProperties properties;

    public UnsubscribeTokens(AppProperties properties) {
        this.properties = properties;
    }

    private byte[] mac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw ApiError.internal("Unsubscribe token key failure");
        }
    }

    public String sign(long guestId) {
        byte[] payload = Long.toString(guestId).getBytes(StandardCharsets.UTF_8);
        String signature = HexFormat.of().formatHex(mac(payload));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                + "." + signature;
    }

    /** Returns the guest id when the token verifies, or {@code null}. */
    public Long verify(String token) {
        int dot = token.indexOf('.');
        if (dot < 0) {
            return null;
        }
        byte[] payload;
        byte[] signature;
        try {
            payload = Base64.getUrlDecoder().decode(token.substring(0, dot));
            signature = HexFormat.of().parseHex(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
        byte[] expected;
        try {
            expected = mac(payload);
        } catch (ApiError e) {
            return null;
        }
        if (!java.security.MessageDigest.isEqual(expected, signature)) {
            return null;
        }
        try {
            return Long.parseLong(new String(payload, StandardCharsets.UTF_8));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
