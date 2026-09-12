package com.hotelapp.paymentrecovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.bookings.BookingRelease;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.portal.PortalAuth;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the pure payment-recovery helpers mirrored from
 * services/payment_retry.rs and services/bookings.rs.
 */
class PaymentRecoveryContractTest {

    // ---- capability tokens (payment_retry.rs tests) ------------------------

    @Test
    void mintedTokensAreHexAndHashToAPrefixedDigest() {
        String token = PortalAuth.generateSessionToken();
        assertTrue(PaymentRecovery.isWellFormedCapabilityToken(token));
        String hash = PaymentRecovery.persistCapabilityToken(token);
        assertTrue(hash.startsWith("sha256:"));
        assertNotEquals(hash, token, "the raw token must never be the stored value");
        assertEquals(hash, PaymentRecovery.persistCapabilityToken(token),
                "hashing is stable");
    }

    @Test
    void aPersistedHashIsNotAcceptedAsAPresentedToken() {
        // The stored value contains ':', so replaying a database dump as a URL
        // token fails the shape check before any lookup happens.
        String stored = PaymentRecovery.persistCapabilityToken(
                PortalAuth.generateSessionToken());
        assertFalse(PaymentRecovery.isWellFormedCapabilityToken(stored));
    }

    @Test
    void distinctTokensHashDistinctly() {
        assertNotEquals(PaymentRecovery.persistCapabilityToken("a"),
                PaymentRecovery.persistCapabilityToken("b"));
    }

    @Test
    void theHoldDeadlineCapsTheTtlButNeverExtendsIt() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime full =
                now.plusMinutes(PaymentRecovery.RETRY_CAPABILITY_TTL_MINUTES);

        // No hold: the plain TTL applies.
        assertEquals(full, PaymentRecovery.capabilityExpiryAt(now, null));

        // Hold lands first: it wins.
        OffsetDateTime early = now.plusMinutes(10);
        assertEquals(early, PaymentRecovery.capabilityExpiryAt(now, early));

        // Hold lands later: the TTL still wins, so a distant hold cannot
        // stretch a recovery link beyond an hour.
        assertEquals(full, PaymentRecovery.capabilityExpiryAt(now, now.plusHours(30)));
    }

    @Test
    void aLapsedHoldYieldsAnExpiryThatIsNotInTheFuture() {
        OffsetDateTime now = OffsetDateTime.now();
        assertFalse(PaymentRecovery
                .capabilityExpiryAt(now, now.minusMinutes(5))
                .isAfter(now));
    }

    // ---- is_recoverable -----------------------------------------------------

    @Test
    void aSettledBookingIsNeverOfferedARepaymentLink() {
        for (String settled : new String[] {"paid", "paid_rate", "refunded", "void"}) {
            assertFalse(PaymentRecovery.isRecoverable("pending_payment", settled),
                    settled + " must not be offered repayment");
        }
        assertTrue(PaymentRecovery.isRecoverable("pending_payment", "unpaid"));
        assertTrue(PaymentRecovery.isRecoverable("pending_payment", "partial"));
        assertTrue(PaymentRecovery.isRecoverable("pending", "unpaid_deposit"));
    }

    @Test
    void anInHouseOrDepartedBookingIsNotOfferedALink() {
        for (String status : new String[] {
                "checked_in", "auto_checked_in", "checked_out", "completed",
                "no_show", "voided", "comp_void",
                // Not awaiting payment either: the payment layer refuses these.
                "confirmed", "pending_confirmation"}) {
            assertFalse(PaymentRecovery.isRecoverable(status, "unpaid"),
                    status + " is settled at the desk, not by email");
        }
    }

    // ---- validate_release_reason (services/bookings.rs) ---------------------

    @Test
    void releaseReasonIsRequiredAndBounded() {
        assertThrows(ApiError.class, () -> BookingRelease.validateReleaseReason(null));
        assertThrows(ApiError.class, () -> BookingRelease.validateReleaseReason("   "));
        assertThrows(ApiError.class, () -> BookingRelease.validateReleaseReason("abc"));
        assertThrows(ApiError.class,
                () -> BookingRelease.validateReleaseReason("x".repeat(501)));
        assertEquals("stale hold", BookingRelease.validateReleaseReason("  stale hold  "));
    }

    // ---- is_auto_releasable_source (services/bookings.rs) -------------------

    @Test
    void onlyOnlineChannelsAutoRelease() {
        assertTrue(BookingRelease.isAutoReleasableSource("website"));
        assertTrue(BookingRelease.isAutoReleasableSource(" ONLINE "));
        assertTrue(BookingRelease.isAutoReleasableSource("Website"));
        // Front-desk and unknown sources are exempt regardless of staleness.
        assertFalse(BookingRelease.isAutoReleasableSource(null));
        assertFalse(BookingRelease.isAutoReleasableSource("front_desk"));
        assertFalse(BookingRelease.isAutoReleasableSource("phone"));
        assertFalse(BookingRelease.isAutoReleasableSource(""));
    }
}
