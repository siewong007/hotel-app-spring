package com.hotelapp.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.core.error.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Contract tests for the pure guest-portal helpers mirrored from the Rust backend. */
class PortalContractTest {

    // ---- session / booking tokens -------------------------------------

    @Test
    void sessionTokensAreUniqueHexSecrets() {
        String a = PortalAuth.generateSessionToken();
        String b = PortalAuth.generateSessionToken();
        assertEquals(64, a.length());
        assertNotEquals(a, b);
        assertTrue(a.chars().allMatch(c -> Character.digit(c, 16) >= 0));
    }

    @Test
    void sessionTokenHashIsStableSha256() {
        // sha256("abc") — cross-checked against the Rust ring::digest output.
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                PortalAuth.hashSessionToken("abc"));
    }

    @Test
    void persistedBookingTokenCarriesSha256Prefix() {
        String stored = PortalAuth.persistBookingAccessToken("raw-token");
        assertTrue(stored.startsWith("sha256:"));
        assertEquals("sha256:" + PortalAuth.hashSessionToken("raw-token"), stored);
    }

    @Test
    void bookingTokenMatchesBothHashedAndLegacyRows() {
        String raw = "0123456789abcdef";
        String hashed = PortalAuth.persistBookingAccessToken(raw);
        assertTrue(PortalAuth.bookingAccessTokenMatches(raw, hashed));
        assertFalse(PortalAuth.bookingAccessTokenMatches("fedcba9876543210", hashed));
        // Pre-cutover rows stored the raw token.
        assertTrue(PortalAuth.bookingAccessTokenMatches(raw, raw));
        assertFalse(PortalAuth.bookingAccessTokenMatches("other", raw));
    }

    @Test
    void implausibleTokensAreRejectedBeforeRateLimiting() {
        for (String bad : new String[] {"", "  ", "zzzz", "a".repeat(129), "token with spaces"}) {
            ApiError error = assertThrows(ApiError.class,
                    () -> PortalAuth.ensurePlausiblePortalToken(bad));
            assertEquals(ApiError.Kind.BAD_REQUEST, error.kind());
        }
        // Hex and UUID-shaped values pass.
        PortalAuth.ensurePlausiblePortalToken("0123456789abcdefABCDEF-");
    }

    @Test
    void headerTokenWinsOverPathToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-booking-access-token", "abcdef0123");
        assertEquals("abcdef0123",
                PortalAuth.resolveBookingAccessToken(request, "0123456789"));

        MockHttpServletRequest pathOnly = new MockHttpServletRequest();
        assertEquals("0123456789",
                PortalAuth.resolveBookingAccessToken(pathOnly, " 0123456789 "));

        ApiError error = assertThrows(ApiError.class,
                () -> PortalAuth.resolveBookingAccessToken(new MockHttpServletRequest(), null));
        assertEquals(ApiError.Kind.UNAUTHORIZED, error.kind());
    }

    @Test
    void bearerTokenRequiresBearerScheme() {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        assertEquals(ApiError.Kind.UNAUTHORIZED,
                assertThrows(ApiError.class, () -> PortalAuth.bearerToken(missing)).kind());

        MockHttpServletRequest basic = new MockHttpServletRequest();
        basic.addHeader("Authorization", "Basic abc");
        assertEquals(ApiError.Kind.UNAUTHORIZED,
                assertThrows(ApiError.class, () -> PortalAuth.bearerToken(basic)).kind());

        MockHttpServletRequest ok = new MockHttpServletRequest();
        ok.addHeader("Authorization", "Bearer tok123");
        assertEquals("tok123", PortalAuth.bearerToken(ok));
    }

    // ---- eKYC status normalisation (auto_checkin.rs) -------------------

    @Test
    void ekycStatusesNormalizeToUpstreamBuckets() {
        assertEquals("approved", AutoCheckin.normalizeEkycStatus("approved"));
        assertEquals("approved", AutoCheckin.normalizeEkycStatus("verified"));
        assertEquals("rejected", AutoCheckin.normalizeEkycStatus("rejected"));
        assertEquals("expired", AutoCheckin.normalizeEkycStatus("expired"));
        assertEquals("void", AutoCheckin.normalizeEkycStatus("cancelled"));
        assertEquals("in_review", AutoCheckin.normalizeEkycStatus("pending_manual_review"));
        assertEquals("in_review", AutoCheckin.normalizeEkycStatus("escalated"));
        assertEquals("pending", AutoCheckin.normalizeEkycStatus("draft"));
        assertEquals("pending", AutoCheckin.normalizeEkycStatus("submitted"));
        assertEquals("pending", AutoCheckin.normalizeEkycStatus("unmapped-status"));
    }

    // ---- receipt file signatures (payments.rs::receipt_extension) ------

    @Test
    void receiptExtensionDetectsAcceptedSignatures() {
        assertEquals("image/jpeg", PortalPayments.receiptExtension(
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00})[1]);
        assertEquals("png", PortalPayments.receiptExtension(
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})[0]);
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertEquals("webp", PortalPayments.receiptExtension(webp)[0]);
        assertEquals("application/pdf",
                PortalPayments.receiptExtension("%PDF-1.7".getBytes())[1]);
    }

    @Test
    void receiptExtensionRejectsUnknownAndShortBytes() {
        assertNull(PortalPayments.receiptExtension(new byte[0]));
        assertNull(PortalPayments.receiptExtension("not-a-file".getBytes()));
        // Truncated PNG signature must not match.
        assertNull(PortalPayments.receiptExtension(new byte[] {(byte) 0x89, 'P', 'N'}));
    }
}
