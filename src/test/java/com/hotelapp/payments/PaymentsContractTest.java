package com.hotelapp.payments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.core.error.ApiError;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the pure staff-payments helpers mirrored from
 * repositories/payment.rs, services/payments.rs and core/rate_limiter.rs.
 */
class PaymentsContractTest {

    // ---- normalized_idempotency_key (services/payments.rs) ------------------

    @Test
    void idempotencyKeyIsTrimmedAndBounded() {
        assertEquals("abc", StaffPayments.normalizedIdempotencyKey("  abc  "));
        assertThrows(ApiError.class,
                () -> StaffPayments.normalizedIdempotencyKey(null));
        assertThrows(ApiError.class,
                () -> StaffPayments.normalizedIdempotencyKey("   "));
        assertThrows(ApiError.class,
                () -> StaffPayments.normalizedIdempotencyKey("x".repeat(161)));
        assertEquals("x".repeat(160),
                StaffPayments.normalizedIdempotencyKey("x".repeat(160)));
    }

    @Test
    void idempotencyKeyErrorMatchesUpstream() {
        ApiError error = assertThrows(ApiError.class,
                () -> StaffPayments.normalizedIdempotencyKey(""));
        assertEquals("Idempotency key must be between 1 and 160 characters",
                error.getMessage());
    }

    // ---- canonical_payment_fingerprint (repositories/payment.rs) -----------

    @Test
    void fingerprintIsDeterministicAndFieldSensitive() {
        String a = PaymentRepo.canonicalPaymentFingerprint(1L,
                new BigDecimal("100.00"), "cash", "booking", "REF-1", "note",
                "2026-09-01");
        String b = PaymentRepo.canonicalPaymentFingerprint(1L,
                new BigDecimal("100.00"), "cash", "booking", "REF-1", "note",
                "2026-09-01");
        assertEquals(a, b);
        assertEquals(64, a.length(), "sha256 hex");

        for (String mutated : new String[] {
                PaymentRepo.canonicalPaymentFingerprint(2L,
                        new BigDecimal("100.00"), "cash", "booking", "REF-1",
                        "note", "2026-09-01"),
                PaymentRepo.canonicalPaymentFingerprint(1L,
                        new BigDecimal("100.01"), "cash", "booking", "REF-1",
                        "note", "2026-09-01"),
                PaymentRepo.canonicalPaymentFingerprint(1L,
                        new BigDecimal("100.00"), "card", "booking", "REF-1",
                        "note", "2026-09-01"),
                PaymentRepo.canonicalPaymentFingerprint(1L,
                        new BigDecimal("100.00"), "cash", "deposit", "REF-1",
                        "note", "2026-09-01"),
                PaymentRepo.canonicalPaymentFingerprint(1L,
                        new BigDecimal("100.00"), "cash", "booking", "REF-2",
                        "note", "2026-09-01"),
                PaymentRepo.canonicalPaymentFingerprint(1L,
                        new BigDecimal("100.00"), "cash", "booking", "REF-1",
                        "note2", "2026-09-01"),
                PaymentRepo.canonicalPaymentFingerprint(1L,
                        new BigDecimal("100.00"), "cash", "booking", "REF-1",
                        "note", "2026-09-02"),
                PaymentRepo.canonicalPaymentFingerprint(1L,
                        new BigDecimal("100.00"), "cash", "booking", "REF-1",
                        "note", null)}) {
            assertNotEquals(a, mutated);
        }
    }

    @Test
    void fingerprintNormalizesAmountLikeRustDecimalNormalize() {
        // rust_decimal::normalize() drops trailing zeros — 100.00 == 100.
        String scaled = PaymentRepo.canonicalPaymentFingerprint(1L,
                new BigDecimal("100.00"), "cash", "booking", null, null, null);
        String plain = PaymentRepo.canonicalPaymentFingerprint(1L,
                new BigDecimal("100"), "cash", "booking", null, null, null);
        assertEquals(scaled, plain);
    }

    @Test
    void fingerprintDistinguishesNullFromEmptyAndLengthCollisions() {
        // Fields are length-prefixed (`name:S:<len>:<value>` vs `name:N`) so a
        // concatenated tuple can never collide across field boundaries.
        String nullRef = PaymentRepo.canonicalPaymentFingerprint(1L,
                new BigDecimal("1"), "cash", "booking", null, "x", null);
        String emptyRef = PaymentRepo.canonicalPaymentFingerprint(1L,
                new BigDecimal("1"), "cash", "booking", "", "x", null);
        assertNotEquals(nullRef, emptyRef);
    }

    // ---- transaction_reference_conflict (repositories/payment.rs) ----------

    @Test
    void sameBookingConflictNamesNoOtherBooking() {
        // The same-booking branch needs no booking-label lookup.
        PaymentRepo repo = new PaymentRepo(null);
        ApiError error = repo.transactionReferenceConflict(7L, 7L);
        assertEquals(ApiError.Kind.CONFLICT, error.kind());
        assertEquals("This reference is already on another payment for this "
                + "booking, recorded with different details. Use a different "
                + "reference, or leave it blank.", error.getMessage());
    }
}
