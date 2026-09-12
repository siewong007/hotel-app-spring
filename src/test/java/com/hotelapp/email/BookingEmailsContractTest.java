package com.hotelapp.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.i18n.Locales;
import com.hotelapp.core.i18n.Locales.Locale;
import com.hotelapp.email.BookingEmails.BookingEmailSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Mirrors the pure-function tests in upstream {@code services/booking_emails.rs}:
 * localized stay details, name fallbacks, money formatting and the
 * payment-confirmation CTA split between portal and wizard links.
 */
class BookingEmailsContractTest {

    private static BookingEmailSource source() {
        return new BookingEmailSource(
                1L, "Aisha Rahman", null, null, "aisha@example.com",
                "BK-2026-0042", LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 8),
                new BigDecimal("450.00"), "MYR", "1203", "Deluxe King", "ms");
    }

    private static Locale locale(String tag) {
        return Locales.parse(tag);
    }

    @Test
    void stayDetailsRenderInTheGuestLanguage() {
        String malay = source().stayBlockHtml(locale("ms"));
        assertTrue(malay.contains("Tempahan"), "missing the Malay 'Booking' label");
        assertTrue(malay.contains("Bilik"), "missing the Malay 'Room' label");
        assertTrue(malay.contains("Penginapan"), "missing the Malay 'Stay' label");
        assertTrue(malay.contains("Jumlah"), "missing the Malay 'Total' label");
        // August abbreviates to "Ogo" in Malay, not "Aug".
        assertTrue(malay.contains("05 Ogo 2026"), "check-in not localised");
        assertTrue(malay.contains("08 Ogo 2026"), "check-out not localised");
        assertFalse(malay.contains("Aug"), "English month leaked into the Malay email");
    }

    @Test
    void stayDetailsStillRenderInEnglishByDefault() {
        String english = source().stayBlockHtml(locale("en"));
        assertTrue(english.contains("Booking"));
        assertTrue(english.contains("05 Aug 2026"));
        assertTrue(english.contains("08 Aug 2026"));
    }

    @Test
    void plainTextStayBlockIsLocalisedAndCarriesEveryField() {
        String text = source().stayBlockText(locale("ms"));
        for (String expected : new String[] {
                "Tempahan: BK-2026-0042", "Bilik: 1203 (Deluxe King)",
                "05 Ogo 2026", "08 Ogo 2026", "MYR 450.00"}) {
            assertTrue(text.contains(expected), "missing " + expected + " in: " + text);
        }
        assertFalse(text.contains("{{"), "an unresolved placeholder reached the body");
    }

    @Test
    void nightsAreCountedFromTheStayDates() {
        assertEquals(3, source().nights());
    }

    @Test
    void missingGuestAndBookingNamesFallBackInTheRightLanguage() {
        BookingEmailSource anonymous = new BookingEmailSource(
                1L, null, null, null, "aisha@example.com", "   ",
                LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 8),
                new BigDecimal("450.00"), "MYR", "1203", "Deluxe King", null);
        assertEquals("Tetamu", anonymous.guestName(locale("ms")));
        assertEquals("tempahan anda", anonymous.bookingLabel(locale("ms")));
        assertEquals("Guest", anonymous.guestName(locale("en")));
        assertEquals("your booking", anonymous.bookingLabel(locale("en")));
    }

    @Test
    void displayGuestNamePrefersALegalNameOnceBothHalvesExist() {
        // Upstream display_guest_name: nick only wins until check-in supplies
        // a complete legal name.
        assertEquals("Aisha Rahman",
                BookingEmails.displayGuestName("CoolAlex", "Aisha", "Rahman"));
        assertEquals("CoolAlex",
                BookingEmails.displayGuestName("CoolAlex", "Aisha", null));
        assertEquals("CoolAlex",
                BookingEmails.displayGuestName("CoolAlex", null, "Rahman"));
        assertEquals("CoolAlex",
                BookingEmails.displayGuestName("CoolAlex", "  ", "  "));
    }

    @Test
    void anUnsupportedStoredPreferenceDegradesToEnglish() {
        Locale resolved = Locales.resolve("de-DE", Locales.DEFAULT_LOCALE);
        assertEquals("en", resolved.tag());
    }

    @Test
    void aBlankStoredPreferenceFallsThroughToTheHotelDefault() {
        Locale resolved = Locales.resolve("", "ms");
        assertEquals("ms", resolved.tag());
    }

    @Test
    void moneyKeepsTheBookingsOwnCurrencyRegardlessOfLanguage() {
        assertEquals("MYR 450.00", source().money(new BigDecimal("450.00")));
        BookingEmailSource noCurrency = new BookingEmailSource(
                1L, "Aisha", null, null, "a@example.com", "BK-1",
                LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 8),
                new BigDecimal("125.50"), null, "1", "Std", null);
        assertEquals("125.50", noCurrency.money(new BigDecimal("125.50")));
    }

    @Test
    void paymentConfirmationSendsAnAccountHolderToThePortal() {
        AppProperties props = new AppProperties();
        String[] cta = BookingEmails.paymentConfirmationCta(locale("en"), props, null);
        assertTrue(cta[0].endsWith("/portal"), "unexpected CTA url: " + cta[0]);
        assertEquals("View your booking", cta[1]);
    }

    @Test
    void paymentConfirmationSendsAnAccountlessGuestIntoPreCheckIn() {
        AppProperties props = new AppProperties();
        String[] cta = BookingEmails.paymentConfirmationCta(
                locale("en"), props, "deadbeefcafebabe");
        assertTrue(cta[0].endsWith("/guest-checkin/form?token=deadbeefcafebabe"),
                "an accountless guest must land in the wizard, not a sign-in wall");
        assertEquals("Start online check-in", cta[1]);
    }

    @Test
    void paymentConfirmationCtaIsLocalised() {
        AppProperties props = new AppProperties();
        String[] cta = BookingEmails.paymentConfirmationCta(
                locale("ms"), props, "deadbeefcafebabe");
        assertEquals("Mula daftar masuk dalam talian", cta[1]);
    }

    @Test
    void paymentConfirmationIgnoresABlankToken() {
        // A blank token would render "?token=" and send the guest to a page
        // that can only fail; the portal link is the safer fallback.
        AppProperties props = new AppProperties();
        String[] cta = BookingEmails.paymentConfirmationCta(locale("en"), props, "   ");
        assertTrue(cta[0].endsWith("/portal"), "unexpected CTA url: " + cta[0]);
    }
}
