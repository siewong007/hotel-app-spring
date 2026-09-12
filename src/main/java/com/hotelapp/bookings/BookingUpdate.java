package com.hotelapp.bookings;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/** {@code BookingUpdateInput} — every field optional, absent means unchanged. */
public record BookingUpdate(
        @JsonProperty("room_id") String roomId,
        @JsonProperty("check_in_date") String checkInDate,
        @JsonProperty("check_out_date") String checkOutDate,
        @JsonProperty("actual_check_out") String actualCheckOut,
        @JsonProperty("total_amount") Double totalAmount,
        @JsonProperty("status") String status,
        @JsonProperty("payment_status") String paymentStatus,
        @JsonProperty("post_type") String postType,
        @JsonProperty("rate_code") String rateCode,
        @JsonProperty("is_tourist") Boolean isTourist,
        @JsonProperty("tourism_tax_amount") Double tourismTaxAmount,
        @JsonProperty("extra_bed_count") Integer extraBedCount,
        @JsonProperty("extra_bed_charge") Double extraBedCharge,
        @JsonProperty("late_checkout_penalty") Double lateCheckoutPenalty,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("market_code") String marketCode,
        @JsonProperty("discount_percentage") Double discountPercentage,
        @JsonProperty("rate_override_weekday") Double rateOverrideWeekday,
        @JsonProperty("rate_override_weekend") Double rateOverrideWeekend,
        @JsonProperty("check_in_time") String checkInTime,
        @JsonProperty("check_out_time") String checkOutTime,
        @JsonProperty("deposit_paid") Boolean depositPaid,
        @JsonProperty("deposit_amount") Double depositAmount,
        @JsonProperty("company_id") Long companyId,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("clear_company") Boolean clearCompany,
        @JsonProperty("payment_note") String paymentNote,
        @JsonProperty("remarks") String remarks,
        @JsonProperty("special_requests") String specialRequests,
        @JsonProperty("source") String source,
        @JsonProperty("booking_channel_id") Long bookingChannelId,
        @JsonProperty("ota_reference") String otaReference,
        @JsonProperty("room_rate_override") Double roomRateOverride,
        @JsonProperty("daily_rates") Map<String, Object> dailyRates,
        @JsonProperty("cleaning_preference") Boolean cleaningPreference) {
}
