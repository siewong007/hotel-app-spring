package com.hotelapp.bookings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.core.error.ApiError;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the pure-function tests in upstream {@code repositories::bookings::
 * lifecycle} ({@code checkout_payment_guard_tests}) and {@code utils::date}:
 * checkout balance math, flexible date/datetime parsing, and OTA-reference
 * sanitization.
 */
class BookingLifecycleContractTest {

    @Test
    void checkoutBalanceDueIsZeroWhenPaidOrOverpaid() {
        assertEquals(BigDecimal.ZERO, BookingLifecycle.checkoutBalanceDue(
                new BigDecimal("100.00"), new BigDecimal("100.00")));
        assertEquals(BigDecimal.ZERO, BookingLifecycle.checkoutBalanceDue(
                new BigDecimal("100.00"), new BigDecimal("125.00")));
    }

    @Test
    void checkoutBalanceDueReturnsRemainingUnpaidAmount() {
        assertEquals(new BigDecimal("75.00"), BookingLifecycle.checkoutBalanceDue(
                new BigDecimal("100.00"), new BigDecimal("25.00")));
    }

    @Test
    void parseDateFlexibleAcceptsDateOnlyValues() {
        assertEquals(LocalDate.of(2026, 5, 26),
                BookingLifecycle.parseDateFlexible("2026-05-26", "check-in"));
    }

    @Test
    void parseDateFlexibleAcceptsTimestampValuesByDatePart() {
        assertEquals(LocalDate.of(2026, 5, 26),
                BookingLifecycle.parseDateFlexible("2026-05-26T14:30:00+08:00",
                        "check-in"));
    }

    @Test
    void parseDateFlexibleRejectsInvalidValues() {
        ApiError error = assertThrows(ApiError.class,
                () -> BookingLifecycle.parseDateFlexible("26-05-2026", "check-in"));
        assertTrue(error.getMessage().startsWith("Invalid check-in date"));
    }

    @Test
    void parseDateTimeFlexibleDefaultsDateOnlyToNoon() {
        assertEquals(LocalDateTime.of(2026, 6, 27, 12, 0, 0),
                BookingLifecycle.parseDateTimeFlexible("2026-06-27"));
    }

    @Test
    void parseDateTimeFlexibleAcceptsDatetimeLocal() {
        assertEquals(LocalDateTime.of(2026, 6, 27, 9, 30, 0),
                BookingLifecycle.parseDateTimeFlexible("2026-06-27T09:30"));
    }

    @Test
    void parseDateTimeFlexibleAcceptsRfc3339WithOffset() {
        assertEquals(LocalDateTime.of(2026, 6, 27, 14, 30, 0),
                BookingLifecycle.parseDateTimeFlexible("2026-06-27T14:30:00+08:00"));
    }

    @Test
    void parseDateTimeFlexibleRejectsInvalidValues() {
        assertThrows(ApiError.class,
                () -> BookingLifecycle.parseDateTimeFlexible("not-a-date"));
    }

    @Test
    void sanitizeOtaReferenceTrimsAndDropsEmpty() {
        assertNull(BookingLifecycle.sanitizeOtaReference(null));
        assertNull(BookingLifecycle.sanitizeOtaReference("   "));
        assertEquals("ABC-123",
                BookingLifecycle.sanitizeOtaReference("  ABC-123  "));
    }

    @Test
    void sanitizeOtaReferenceTruncatesTo100Chars() {
        String longRef = "x".repeat(150);
        String sanitized = BookingLifecycle.sanitizeOtaReference(longRef);
        assertEquals(100, sanitized.length());
    }
}
