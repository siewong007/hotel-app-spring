package com.hotelapp.guestbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.guestbooking.FunnelModels.AnonymousGuestDetails;
import com.hotelapp.guestbooking.FunnelModels.NightlyRate;
import com.hotelapp.guestbooking.FunnelModels.ValidatedStay;
import com.hotelapp.guestbooking.FunnelModels.VoucherPricing;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for the pure funnel helpers mirrored from guest_booking/service+validation. */
class FunnelContractTest {

    private final FunnelValidation validation = new FunnelValidation();

    private static VoucherPricing voucher(String type, long value, Long max) {
        return new VoucherPricing(1, 2, "Deal", type, BigDecimal.valueOf(value),
                max == null ? null : BigDecimal.valueOf(max));
    }

    private static OffsetDateTime at(String date) {
        return LocalDate.parse(date).atTime(9, 0).atOffset(ZoneOffset.UTC);
    }

    // ---- tourism tax (service.rs::tourism_tax_for_type) -----------------

    @Test
    void localAndBlankTourismTypesAreNotCharged() {
        assertEquals(BigDecimal.ZERO, FunnelService.tourismTaxForType("local", 3, BigDecimal.TEN));
        assertEquals(BigDecimal.ZERO, FunnelService.tourismTaxForType("", 3, BigDecimal.TEN));
        assertEquals(BigDecimal.ZERO, FunnelService.tourismTaxForType("LOCAL", 1, BigDecimal.TEN));
    }

    @Test
    void foreignGuestsPayRateTimesNights() {
        assertEquals(BigDecimal.valueOf(20),
                FunnelService.tourismTaxForType("foreign", 2, BigDecimal.TEN));
        assertEquals(BigDecimal.TEN,
                FunnelService.tourismTaxForType("Foreign", 1, BigDecimal.TEN));
    }

    @Test
    void zeroNightStayStillBillsOneNightOfTourismTax() {
        assertEquals(BigDecimal.TEN,
                FunnelService.tourismTaxForType("foreign", 0, BigDecimal.TEN));
    }

    // ---- anonymous access-token expiry windows -------------------------

    @Test
    void nearStayKeepsFlatMinimum() {
        OffsetDateTime expiry = FunnelService.anonymousAccessTokenExpiry(
                at("2026-09-05"), LocalDate.parse("2026-09-10"));
        assertEquals(at("2026-09-05").plusDays(14), expiry);
    }

    @Test
    void distantStayHoldsUntilVerifyCanReissue() {
        OffsetDateTime expiry = FunnelService.anonymousAccessTokenExpiry(
                at("2026-09-05"), LocalDate.parse("2026-12-01"));
        assertEquals(LocalDate.parse("2026-11-24").atStartOfDay().atOffset(ZoneOffset.UTC),
                expiry);
    }

    @Test
    void theTwoWindowsNeverLeaveAGap() {
        OffsetDateTime now = at("2026-09-05");
        for (long offset : new long[] {0, 1, 7, 13, 14, 15, 30, 90}) {
            LocalDate checkIn = LocalDate.parse("2026-09-05").plusDays(offset);
            OffsetDateTime expiry = FunnelService.anonymousAccessTokenExpiry(now, checkIn);
            OffsetDateTime verifyOpens = checkIn.atStartOfDay().atOffset(ZoneOffset.UTC)
                    .minusDays(7);
            assertTrue(!expiry.isBefore(verifyOpens)
                            || !expiry.isBefore(checkIn.atStartOfDay().atOffset(ZoneOffset.UTC)),
                    "gap for check-in in " + offset + " days");
        }
    }

    @Test
    void idempotentRetryKeepsUnexpiredDeadline() {
        OffsetDateTime now = at("2026-09-05");
        OffsetDateTime stored = now.plusDays(20);
        assertEquals(stored, FunnelService.replayAnonymousAccessTokenExpiry(
                now, LocalDate.parse("2026-12-01"), stored));
    }

    @Test
    void idempotentRetryRecomputesWhenStoredDeadlineIsGoneOrPast() {
        OffsetDateTime now = at("2026-09-05");
        LocalDate checkIn = LocalDate.parse("2026-12-01");
        OffsetDateTime expected = FunnelService.anonymousAccessTokenExpiry(now, checkIn);
        assertEquals(expected, FunnelService.replayAnonymousAccessTokenExpiry(now, checkIn, null));
        assertEquals(expected, FunnelService.replayAnonymousAccessTokenExpiry(
                now, checkIn, now.minusSeconds(1)));
    }

    // ---- voucher + settlement maths -------------------------------------

    @Test
    void percentageVoucherIsCapped() {
        assertEquals(BigDecimal.valueOf(10).setScale(2),
                FunnelService.voucherDiscount(BigDecimal.valueOf(100),
                        voucher("percentage", 25, 10L)));
    }

    @Test
    void fixedVoucherCannotMakeTotalNegative() {
        assertEquals(BigDecimal.valueOf(100).setScale(2),
                FunnelService.voucherDiscount(BigDecimal.valueOf(100),
                        voucher("fixed_amount", 250, null)));
    }

    private static NightlyRate rate(int day, long amount) {
        return new NightlyRate(LocalDate.of(2026, 8, day), "BASE", BigDecimal.valueOf(amount));
    }

    @Test
    void complimentaryDiscountUsesEachCompedNightsRate() {
        List<NightlyRate> rates = List.of(rate(10, 100), rate(11, 300), rate(12, 200));
        assertEquals(BigDecimal.valueOf(300).setScale(2),
                FunnelService.complimentaryDiscount(rates, List.of(LocalDate.of(2026, 8, 11))));
    }

    @Test
    void complimentaryDiscountIgnoresDatesOutsideTheStay() {
        List<NightlyRate> rates = List.of(rate(10, 100), rate(11, 300));
        assertEquals(BigDecimal.ZERO.setScale(2),
                FunnelService.complimentaryDiscount(rates, List.of(LocalDate.of(2026, 8, 20))));
    }

    @Test
    void creditsCoveringEveryNightLeaveNothingToPay() {
        BigDecimal comped = FunnelService.complimentaryDiscount(
                List.of(rate(10, 100), rate(11, 300)),
                List.of(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 11)));
        BigDecimal[] settled = FunnelService.settlement(BigDecimal.valueOf(400), comped, null);
        assertEquals(BigDecimal.valueOf(400).setScale(2), settled[0]);
        assertEquals(BigDecimal.ZERO.setScale(2), settled[1]);
    }

    @Test
    void percentageVoucherDiscountsOnlyWhatCreditsLeftPayable() {
        BigDecimal[] settled = FunnelService.settlement(BigDecimal.valueOf(400),
                BigDecimal.valueOf(300), voucher("percentage", 25, null));
        assertEquals(BigDecimal.valueOf(325).setScale(2), settled[0]);
        assertEquals(BigDecimal.valueOf(75).setScale(2), settled[1]);
    }

    @Test
    void creditsAndVoucherTogetherNeverProduceNegativeTotal() {
        BigDecimal[] settled = FunnelService.settlement(BigDecimal.valueOf(400),
                BigDecimal.valueOf(300), voucher("fixed_amount", 500, null));
        assertEquals(BigDecimal.valueOf(400).setScale(2), settled[0]);
        assertEquals(BigDecimal.ZERO.setScale(2), settled[1]);
    }

    // ---- stay validation ------------------------------------------------

    @Test
    void rejectsZeroNightStay() {
        LocalDate today = LocalDate.of(2026, 7, 17);
        assertThrows(ApiError.class, () -> validation.validateStay(
                "2026-07-17", "2026-07-17", 1, 0, today));
    }

    @Test
    void rejectsStaysMoreThanThreeMonthsAhead() {
        LocalDate today = LocalDate.of(2026, 7, 17);
        LocalDate checkIn = today.plusMonths(3).plusDays(1);
        assertThrows(ApiError.class, () -> validation.validateStay(
                checkIn.toString(), checkIn.plusDays(1).toString(), null, null, today));
    }

    @Test
    void validatesOccupancyDefaultsAndBounds() {
        LocalDate today = LocalDate.of(2026, 7, 17);
        ValidatedStay stay = validation.validateStay("2026-07-18", "2026-07-20",
                null, null, today);
        assertEquals(1, stay.adults());
        assertEquals(0, stay.children());
        assertThrows(ApiError.class, () -> validation.validateStay(
                "2026-07-18", "2026-07-20", 0, 0, today));
        assertThrows(ApiError.class, () -> validation.validateStay(
                "2026-07-18", "2026-07-20", 21, 0, today));
    }

    @Test
    void complimentaryDatesAreSortedAndDeduplicated() {
        ValidatedStay stay = new ValidatedStay(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 13), 1, 0);
        List<LocalDate> dates = validation.validateComplimentaryDates(
                List.of("2026-08-12", "2026-08-10", "2026-08-12"), stay);
        assertEquals(List.of(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12)), dates);
    }

    @Test
    void checkoutDayIsNotANightOfTheStay() {
        ValidatedStay stay = new ValidatedStay(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 13), 1, 0);
        assertThrows(ApiError.class, () -> validation.validateComplimentaryDates(
                List.of("2026-08-13"), stay));
    }

    @Test
    void anonymousGuestUsesFirstNameAsNicknameWithoutLastName() {
        var guest = validation.validateAnonymousGuest(new AnonymousGuestDetails(
                "Alex", "   ", "alex@hotel.test", null, "local"));
        assertEquals("Alex", guest.nickName());
        assertEquals("Alex", guest.firstName());
        assertEquals(null, guest.lastName());
    }

    @Test
    void anonymousGuestRequiresTourismTypeAndValidEmail() {
        assertThrows(ApiError.class, () -> validation.validateAnonymousGuest(
                new AnonymousGuestDetails("Alex", null, "alex@hotel.test", null, null)));
        assertThrows(ApiError.class, () -> validation.validateAnonymousGuest(
                new AnonymousGuestDetails("Alex", null, "not-an-email", null, "local")));
        assertThrows(ApiError.class, () -> validation.validateAnonymousGuest(
                new AnonymousGuestDetails("Alex", null, "alex@hotel.test", null, "other")));
    }

    // ---- booking number shape -------------------------------------------

    @Test
    void bookingNumberCarriesCheckInDateAndEightHexChars() {
        String number = FunnelService.generateBookingNumberForDate(LocalDate.of(2026, 9, 8));
        assertTrue(number.matches("BK-20260908-[0-9a-f]{8}"));
        assertFalse(number.isBlank());
    }
}
