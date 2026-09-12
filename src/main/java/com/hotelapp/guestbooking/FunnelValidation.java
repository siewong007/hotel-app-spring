package com.hotelapp.guestbooking;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.text.Sanitizer;
import com.hotelapp.guestbooking.FunnelModels.AnonymousGuestDetails;
import com.hotelapp.guestbooking.FunnelModels.ValidatedAnonymousGuest;
import com.hotelapp.guestbooking.FunnelModels.ValidatedStay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Port of {@code modules/guest_booking/validation.rs} (+ communications' validate_email). */
@Component
public class FunnelValidation {

    public static final long MAX_BOOKING_NIGHTS = 30;
    public static final long MAX_ADVANCE_BOOKING_MONTHS = 3;

    /**
     * {@code validate_stay} — {@code today} is the hotel business date (the
     * connection's CURRENT_DATE), supplied by the caller's query.
     */
    public ValidatedStay validateStay(String checkIn, String checkOut,
            Integer adults, Integer children, LocalDate today) {
        LocalDate checkInDate = parseDate(checkIn, "Invalid check-in date. Use YYYY-MM-DD");
        LocalDate checkOutDate = parseDate(checkOut, "Invalid check-out date. Use YYYY-MM-DD");
        if (checkInDate.isBefore(today)) {
            throw ApiError.badRequest("Check-in date cannot be in the past");
        }
        LocalDate latestCheckIn = today.plusMonths(MAX_ADVANCE_BOOKING_MONTHS);
        if (checkInDate.isAfter(latestCheckIn)) {
            throw ApiError.badRequest("Bookings can only be made up to "
                    + MAX_ADVANCE_BOOKING_MONTHS + " months in advance");
        }
        long nights = checkOutDate.toEpochDay() - checkInDate.toEpochDay();
        if (nights < 1) {
            throw ApiError.badRequest("Check-out date must be after check-in date");
        }
        if (nights > MAX_BOOKING_NIGHTS) {
            throw ApiError.badRequest("A portal booking cannot exceed "
                    + MAX_BOOKING_NIGHTS + " nights");
        }
        int partyAdults = adults == null ? 1 : adults;
        int partyChildren = children == null ? 0 : children;
        if (partyAdults < 1 || partyAdults > 20 || partyChildren < 0 || partyChildren > 20) {
            throw ApiError.badRequest("Invalid guest occupancy");
        }
        return new ValidatedStay(checkInDate, checkOutDate, partyAdults, partyChildren);
    }

    /**
     * {@code validate_complimentary_dates} — sorted, de-duplicated, every date
     * inside the stay (check-out day is never a stayed night).
     */
    public List<LocalDate> validateComplimentaryDates(List<String> values, ValidatedStay stay) {
        if (values == null) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>(values.size());
        for (String value : values) {
            LocalDate date;
            try {
                date = LocalDate.parse(value.trim());
            } catch (Exception e) {
                throw ApiError.badRequest("Invalid complimentary night date: " + value);
            }
            if (date.isBefore(stay.checkInDate()) || !date.isBefore(stay.checkOutDate())) {
                throw ApiError.badRequest(date + " is not a night of this stay ("
                        + stay.checkInDate() + " to " + stay.checkOutDate() + ")");
            }
            if (!dates.contains(date)) {
                dates.add(date);
            }
        }
        dates.sort(null);
        return dates;
    }

    /** {@code validate_client_request_id}. */
    public String validateClientRequestId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > 128) {
            throw ApiError.badRequest("Invalid client request identifier");
        }
        return trimmed;
    }

    /**
     * {@code validate_anonymous_guest}: nickname required, last name discarded,
     * email mandatory and shaped, phone capped at 20 chars, tourism_type
     * required and never defaulted (it prices tourism tax).
     */
    public ValidatedAnonymousGuest validateAnonymousGuest(AnonymousGuestDetails details) {
        if (details == null) {
            throw ApiError.badRequest("Please enter a nickname");
        }
        String firstName = Sanitizer.sanitizeGuestName(details.firstName());
        if (firstName.isEmpty() || firstName.codePointCount(0, firstName.length()) > 100) {
            throw ApiError.badRequest("Please enter a nickname");
        }
        String email = validateEmail(details.email());
        String phone = details.phone() == null ? null : Sanitizer.sanitizePhone(details.phone());
        if (phone != null && phone.isEmpty()) {
            phone = null;
        }
        if (phone != null && phone.codePointCount(0, phone.length()) > 20) {
            throw ApiError.badRequest("Phone number is too long");
        }
        String tourismType = details.tourismType() == null ? ""
                : details.tourismType().trim().toLowerCase();
        if (!"local".equals(tourismType) && !"foreign".equals(tourismType)) {
            throw ApiError.badRequest(
                    "Please select whether the guest is a local or foreign tourist");
        }
        return new ValidatedAnonymousGuest(firstName, firstName, null, email, phone, tourismType);
    }

    /** {@code communications::validation::validate_email}. */
    public static String validateEmail(String value) {
        String email = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        int len = email.codePointCount(0, email.length());
        boolean shapeOk = len >= 3 && len <= 255
                && email.chars().noneMatch(Character::isWhitespace)
                && email.indexOf('@') > 0
                && email.substring(email.indexOf('@') + 1).contains(".");
        if (!shapeOk) {
            throw ApiError.badRequest("Invalid email address");
        }
        return email;
    }

    private static LocalDate parseDate(String value, String message) {
        try {
            return LocalDate.parse(value == null ? "" : value.trim());
        } catch (Exception e) {
            throw ApiError.badRequest(message);
        }
    }
}
