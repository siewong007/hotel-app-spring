package com.hotelapp.guestbooking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs for {@code modules/guest_booking/models.rs} — field names byte-identical. */
public final class FunnelModels {

    private FunnelModels() {
    }

    // ------------------------- requests ---------------------------------

    /** {@code BookingQuoteRequest}. */
    public record BookingQuoteRequest(
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("check_in_date") String checkInDate,
            @JsonProperty("check_out_date") String checkOutDate,
            @JsonProperty("adults") Integer adults,
            @JsonProperty("children") Integer children,
            @JsonProperty("voucher_id") Long voucherId,
            @JsonProperty("complimentary_dates") List<String> complimentaryDates,
            @JsonProperty("tourism_type") String tourismType) {

        /** The public-quote projection: discounts belong to an account. */
        public BookingQuoteRequest asPublic() {
            return new BookingQuoteRequest(roomTypeId, checkInDate, checkOutDate, adults,
                    children, null, null, tourismType);
        }
    }

    /** {@code AnonymousGuestDetails}. last_name is accepted then discarded. */
    public record AnonymousGuestDetails(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("email") String email,
            @JsonProperty("phone") String phone,
            @JsonProperty("tourism_type") String tourismType) {
    }

    /** {@code AnonymousBookingRequest} — no voucher/credits: account-only. */
    public record AnonymousBookingRequest(
            @JsonProperty("client_request_id") String clientRequestId,
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("check_in_date") String checkInDate,
            @JsonProperty("check_out_date") String checkOutDate,
            @JsonProperty("adults") Integer adults,
            @JsonProperty("children") Integer children,
            @JsonProperty("expected_total") BigDecimal expectedTotal,
            @JsonProperty("special_requests") String specialRequests,
            @JsonProperty("cleaning_preference") Boolean cleaningPreference,
            @JsonProperty("guest") AnonymousGuestDetails guest,
            @JsonProperty("consents") List<ConsentAcceptance> consents,
            @JsonProperty("marketing_opt_in") Boolean marketingOptIn) {
    }

    /** {@code CreateGuestBookingRequest}. */
    public record CreateGuestBookingRequest(
            @JsonProperty("client_request_id") String clientRequestId,
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("check_in_date") String checkInDate,
            @JsonProperty("check_out_date") String checkOutDate,
            @JsonProperty("adults") Integer adults,
            @JsonProperty("children") Integer children,
            @JsonProperty("voucher_id") Long voucherId,
            @JsonProperty("complimentary_dates") List<String> complimentaryDates,
            @JsonProperty("expected_total") BigDecimal expectedTotal,
            @JsonProperty("special_requests") String specialRequests,
            @JsonProperty("cleaning_preference") Boolean cleaningPreference,
            @JsonProperty("consents") List<ConsentAcceptance> consents) {
    }

    /** {@code UpdateOnlineInventoryRequest}. */
    public record UpdateOnlineInventoryRequest(
            @JsonProperty("walk_in_reserved_rooms") int walkInReservedRooms,
            @JsonProperty("online_booking_enabled") boolean onlineBookingEnabled,
            @JsonProperty("custom_price") BigDecimal customPrice) {
    }

    // ------------------------- responses --------------------------------

    /** {@code NightlyRate}. */
    public record NightlyRate(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("rate_plan_code") String ratePlanCode,
            @JsonProperty("amount") BigDecimal amount) {
    }

    /** {@code GuestBookingOffer}. */
    public record GuestBookingOffer(
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("room_type_code") String roomTypeCode,
            @JsonProperty("room_type_name") String roomTypeName,
            @JsonProperty("description") String description,
            @JsonProperty("max_occupancy") int maxOccupancy,
            @JsonProperty("bed_type") String bedType,
            @JsonProperty("bed_count") Integer bedCount,
            @JsonProperty("images") List<String> images,
            @JsonProperty("features") List<String> features,
            @JsonProperty("available_rooms") long availableRooms,
            @JsonProperty("currency") String currency,
            @JsonProperty("nightly_rates") List<NightlyRate> nightlyRates,
            @JsonProperty("subtotal") BigDecimal subtotal,
            @JsonProperty("discount_amount") BigDecimal discountAmount,
            @JsonProperty("tax_amount") BigDecimal taxAmount,
            @JsonProperty("total_amount") BigDecimal totalAmount) {
    }

    /** {@code GuestBookingQuote}. */
    public record GuestBookingQuote(
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("room_type_code") String roomTypeCode,
            @JsonProperty("room_type_name") String roomTypeName,
            @JsonProperty("check_in_date") LocalDate checkInDate,
            @JsonProperty("check_out_date") LocalDate checkOutDate,
            @JsonProperty("adults") int adults,
            @JsonProperty("children") int children,
            @JsonProperty("currency") String currency,
            @JsonProperty("nightly_rates") List<NightlyRate> nightlyRates,
            @JsonProperty("subtotal") BigDecimal subtotal,
            @JsonProperty("discount_amount") BigDecimal discountAmount,
            @JsonProperty("tax_amount") BigDecimal taxAmount,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("voucher_id") Long voucherId,
            @JsonProperty("voucher_name") String voucherName,
            @JsonProperty("complimentary_dates") List<LocalDate> complimentaryDates,
            @JsonProperty("complimentary_nights") int complimentaryNights,
            @JsonProperty("complimentary_discount") BigDecimal complimentaryDiscount,
            @JsonProperty("credits_available") int creditsAvailable) {
    }

    /** {@code GuestBookingVoucherOptions}. */
    public record GuestBookingVoucherOptions(
            @JsonProperty("quote") GuestBookingQuote quote,
            @JsonProperty("eligible_voucher_ids") List<Long> eligibleVoucherIds) {
    }

    /** {@code GuestBookingConfirmation}; token fields omit when absent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GuestBookingConfirmation(
            @JsonProperty("booking_id") long bookingId,
            @JsonProperty("booking_number") String bookingNumber,
            @JsonProperty("room_type_name") String roomTypeName,
            @JsonProperty("check_in_date") LocalDate checkInDate,
            @JsonProperty("check_out_date") LocalDate checkOutDate,
            @JsonProperty("status") String status,
            @JsonProperty("payment_status") String paymentStatus,
            @JsonProperty("currency") String currency,
            @JsonProperty("subtotal") BigDecimal subtotal,
            @JsonProperty("discount_amount") BigDecimal discountAmount,
            @JsonProperty("tax_amount") BigDecimal taxAmount,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("created_at") Object createdAt,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("access_token_expires_at") Object accessTokenExpiresAt) {

        public GuestBookingConfirmation withAccessToken(String token, Object expiresAt) {
            return new GuestBookingConfirmation(bookingId, bookingNumber, roomTypeName,
                    checkInDate, checkOutDate, status, paymentStatus, currency, subtotal,
                    discountAmount, taxAmount, totalAmount, createdAt, token, expiresAt);
        }
    }

    /** {@code OnlineInventoryAllocation}. */
    public record OnlineInventoryAllocation(
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("room_type_code") String roomTypeCode,
            @JsonProperty("room_type_name") String roomTypeName,
            @JsonProperty("stay_date") LocalDate stayDate,
            @JsonProperty("physical_available_rooms") long physicalAvailableRooms,
            @JsonProperty("walk_in_reserved_rooms") int walkInReservedRooms,
            @JsonProperty("online_booking_enabled") boolean onlineBookingEnabled,
            @JsonProperty("custom_price") BigDecimal customPrice,
            @JsonProperty("standard_price") BigDecimal standardPrice,
            @JsonProperty("is_override") boolean isOverride,
            @JsonProperty("online_available_rooms") long onlineAvailableRooms) {
    }

    /** {@code OnlineInventoryCellUpdate} — one cell of a bulk write. */
    public record OnlineInventoryCellUpdate(
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("stay_date") String stayDate,
            @JsonProperty("reset") Boolean reset,
            @JsonProperty("walk_in_reserved_rooms") Integer walkInReservedRooms,
            @JsonProperty("online_booking_enabled") Boolean onlineBookingEnabled,
            @JsonProperty("custom_price") BigDecimal customPrice) {
    }

    /** {@code BulkUpdateOnlineInventoryRequest}. */
    public record BulkUpdateOnlineInventoryRequest(
            @JsonProperty("cells") List<OnlineInventoryCellUpdate> cells) {
    }

    /** {@code OnlineInventoryAffectedSpan} — one published range per type. */
    public record OnlineInventoryAffectedSpan(long roomTypeId, LocalDate firstDate,
            LocalDate lastDate) {
    }

    // ------------------------- internals --------------------------------

    /** {@code RoomTypeInventory}. */
    public record RoomTypeInventory(
            long id, String code, String name, String description,
            BigDecimal basePrice, BigDecimal weekdayRate, BigDecimal weekendRate,
            int maxOccupancy, String bedType, Integer bedCount,
            List<String> images, List<String> features, long availableRooms) {

        public RoomTypeInventory withAvailableRooms(long rooms) {
            return new RoomTypeInventory(id, code, name, description, basePrice,
                    weekdayRate, weekendRate, maxOccupancy, bedType, bedCount, images,
                    features, rooms);
        }
    }

    /** {@code VoucherPricing}. */
    public record VoucherPricing(
            long voucherId, long promotionId, String promotionName,
            String discountType, BigDecimal discountValue, BigDecimal maxDiscountAmount) {
    }

    /** {@code GuestContact}. */
    public record GuestContact(Long actorUserId, String nickName, String email) {
    }

    /** {@code ValidatedStay}. */
    public record ValidatedStay(
            LocalDate checkInDate, LocalDate checkOutDate, int adults, int children) {

        public long nights() {
            return checkOutDate.toEpochDay() - checkInDate.toEpochDay();
        }
    }

    /** {@code ValidatedAnonymousGuest} — normalised for storage. */
    public record ValidatedAnonymousGuest(
            String nickName, String firstName, String lastName,
            String email, String phone, String tourismType) {
    }

    /** {@code BookingInsert}. */
    public record BookingInsert(
            String portalRequestId, long guestId, Long actorUserId, long roomId,
            String bookingNumber, LocalDate checkInDate, LocalDate checkOutDate,
            int adults, int children, BigDecimal roomRate, BigDecimal subtotal,
            BigDecimal discountAmount, BigDecimal totalAmount, String currency,
            String specialRequests, Boolean cleaningPreference, Long bookingChannelId,
            java.util.Map<String, BigDecimal> nightlyRates, String complimentaryReason,
            boolean settledByCredits, boolean isTourist, BigDecimal tourismTaxAmount) {
    }

    /** {@code AvailabilityEvent} — published to the availability hub. */
    public record AvailabilityEvent(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("reason") String reason,
            @JsonProperty("room_type_id") Long roomTypeId,
            @JsonProperty("check_in_date") LocalDate checkInDate,
            @JsonProperty("check_out_date") LocalDate checkOutDate,
            @JsonProperty("remaining_rooms") Long remainingRooms) {
    }
}
