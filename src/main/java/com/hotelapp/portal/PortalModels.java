package com.hotelapp.portal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO records for the guest portal surface, mirroring
 * {@code models/guest_portal.rs}, the portal-relevant parts of
 * {@code models/booking.rs}, {@code models/guest.rs} and
 * {@code models/payment.rs} field-for-field (snake_case on the wire).
 */
public final class PortalModels {

    private PortalModels() {
    }

    // ------------------------------------------------------------------
    // Verification + booking access token flow
    // ------------------------------------------------------------------

    public record GuestPortalVerifyRequest(
            @JsonProperty("booking_number") String bookingNumber,
            String name) {
    }

    public record GuestPortalVerifyResponse(
            String token,
            @JsonProperty("expires_at") String expiresAt,
            @JsonProperty("booking_id") String bookingId) {
    }

    public record GuestPortalBookingView(
            long id,
            @JsonProperty("booking_number") String bookingNumber,
            @JsonProperty("check_in_date") String checkInDate,
            @JsonProperty("check_out_date") String checkOutDate,
            String status,
            Integer adults,
            Integer children,
            @JsonProperty("special_requests") String specialRequests,
            @JsonProperty("market_code") String marketCode,
            @JsonProperty("pre_checkin_completed") Boolean preCheckinCompleted,
            @JsonProperty("pre_checkin_completed_at") Object preCheckinCompletedAt) {
    }

    public record GuestPortalGuestView(
            @JsonProperty("nick_name") String nickName,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String title,
            String email,
            String phone,
            @JsonProperty("alt_phone") String altPhone,
            @JsonProperty("ic_number") String icNumber,
            String nationality,
            @JsonProperty("address_line1") String addressLine1,
            String city,
            @JsonProperty("state_province") String stateProvince,
            @JsonProperty("postal_code") String postalCode,
            String country) {
    }

    public record GuestEkycStatusSummary(
            @JsonProperty("guest_id") long guestId,
            @JsonProperty("ekyc_verification_id") Long ekycVerificationId,
            String status,
            @JsonProperty("self_checkin_enabled") boolean selfCheckinEnabled,
            @JsonProperty("verified_at") Object verifiedAt,
            @JsonProperty("can_auto_checkin") boolean canAutoCheckin,
            @JsonProperty("auto_checkin_block_reason") String autoCheckinBlockReason) {

        public static GuestEkycStatusSummary notSubmitted(long guestId) {
            return new GuestEkycStatusSummary(guestId, null, "not_submitted", false, null, false,
                    "eKYC has not been submitted.");
        }
    }

    public record GuestPortalBookingResponse(
            GuestPortalBookingView booking,
            GuestPortalGuestView guest,
            @JsonProperty("ekyc_summary") GuestEkycStatusSummary ekycSummary,
            @JsonProperty("receipt_request_payment_id") Long receiptRequestPaymentId,
            @JsonProperty("receipt_request_message") String receiptRequestMessage,
            @JsonProperty("receipt_uploaded") boolean receiptUploaded) {
    }

    /** mirrors GuestUpdateInput — all fields optional; is_active accepted but ignored. */
    public record GuestUpdateInput(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String email,
            String phone,
            String title,
            @JsonProperty("alt_phone") String altPhone,
            @JsonProperty("ic_number") String icNumber,
            String nationality,
            @JsonProperty("address_line1") String addressLine1,
            String city,
            @JsonProperty("state_province") String stateProvince,
            @JsonProperty("postal_code") String postalCode,
            String country,
            @JsonProperty("is_active") Boolean isActive,
            @JsonProperty("guest_type") String guestType,
            @JsonProperty("tourism_type") String tourismType,
            @JsonProperty("discount_percentage") Integer discountPercentage,
            @JsonProperty("company_name") String companyName) {
    }

    public record PreCheckInUpdateRequest(
            @JsonProperty("guest_update") GuestUpdateInput guestUpdate,
            @JsonProperty("market_code") String marketCode,
            @JsonProperty("special_requests") String specialRequests) {
    }

    public record AutoCheckinResponse(
            boolean success,
            @JsonProperty("booking_id") long bookingId,
            @JsonProperty("room_number") String roomNumber,
            @JsonProperty("digital_key_sent") boolean digitalKeySent,
            @JsonProperty("checked_in_at") Object checkedInAt,
            @JsonProperty("ekyc_summary") GuestEkycStatusSummary ekycSummary,
            String message) {
    }

    // ------------------------------------------------------------------
    // Session + claim-account
    // ------------------------------------------------------------------

    public record GuestPortalLoginResponse(
            String token,
            @JsonProperty("expires_at") java.time.OffsetDateTime expiresAt,
            GuestPortalGuestView guest) {
    }

    public record ConsentAcceptance(
            String document,
            String version,
            boolean granted,
            String locale) {

        public ConsentAcceptance {
            if (locale == null) {
                locale = "en";
            }
        }
    }

    public record GuestPortalClaimAccountRequest(
            @JsonProperty("booking_number") String bookingNumber,
            @JsonProperty("guest_name") String guestName,
            String username,
            String password,
            String email,
            List<ConsentAcceptance> consents,
            @JsonProperty("marketing_opt_in") Boolean marketingOptIn) {
    }

    public record GuestPortalClaimAccountResponse(
            GuestPortalLoginResponse session,
            String username,
            @JsonProperty("email_verification_required") boolean emailVerificationRequired) {
    }

    // ------------------------------------------------------------------
    // Me hub
    // ------------------------------------------------------------------

    public record GuestPortalMeResponse(
            GuestPortalGuestView guest,
            @JsonProperty("profile_complete") boolean profileComplete,
            @JsonProperty("missing_profile_fields") List<String> missingProfileFields) {
    }

    public record GuestPortalProfileUpdate(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String phone,
            @JsonProperty("alt_phone") String altPhone,
            String title,
            String nationality,
            @JsonProperty("address_line1") String addressLine1,
            String city,
            @JsonProperty("state_province") String stateProvince,
            @JsonProperty("postal_code") String postalCode,
            String country) {

        public String nickName() {
            return firstName + " " + lastName;
        }
    }

    public record GuestPortalBookingSummary(
            long id,
            @JsonProperty("booking_number") String bookingNumber,
            @JsonProperty("check_in_date") String checkInDate,
            @JsonProperty("check_out_date") String checkOutDate,
            String status,
            @JsonProperty("total_amount") BigDecimal totalAmount,
            @JsonProperty("completed_payment_id") Long completedPaymentId,
            @JsonProperty("completed_payment_method") String completedPaymentMethod,
            @JsonProperty("completed_payment_amount") BigDecimal completedPaymentAmount,
            @JsonProperty("can_cancel") boolean canCancel,
            @JsonProperty("cancellation_unavailable_reason") String cancellationUnavailableReason,
            @JsonProperty("payment_rejection_reason") String paymentRejectionReason,
            @JsonProperty("receipt_request_payment_id") Long receiptRequestPaymentId,
            @JsonProperty("receipt_request_message") String receiptRequestMessage,
            @JsonProperty("receipt_uploaded") boolean receiptUploaded) {
    }

    public record GuestBookingCancellationRequest(String reason) {
    }

    public record GuestPortalPage<T>(List<T> items, long total) {
    }

    public record GuestPortalTransaction(
            String kind,
            Object date,
            BigDecimal amount,
            String method,
            String reference,
            @JsonProperty("invoice_number") String invoiceNumber,
            @JsonProperty("booking_number") String bookingNumber,
            String status) {
    }

    public record GuestPortalMembership(
            @JsonProperty("member_number") String memberNumber,
            @JsonProperty("tier_name") String tierName,
            @JsonProperty("tier_level") int tierLevel,
            @JsonProperty("points_balance") int pointsBalance,
            @JsonProperty("lifetime_points") int lifetimePoints,
            String status) {
    }

    public record GuestPortalPointsActivity(
            Object date,
            @JsonProperty("transaction_type") String transactionType,
            int points,
            @JsonProperty("balance_after") int balanceAfter,
            String reason,
            @JsonProperty("booking_number") String bookingNumber,
            @JsonProperty("adjusted_by") String adjustedBy) {
    }

    public record GuestPortalMembershipResponse(
            GuestPortalMembership membership,
            @JsonProperty("recent_activity") List<GuestPortalPointsActivity> recentActivity) {
    }

    public record GuestPortalTierBenefit(
            @JsonProperty("tier_name") String tierName,
            @JsonProperty("discount_percentage") BigDecimal discountPercentage) {
    }

    public record GuestPortalReward(
            long id,
            String name,
            String description,
            String category,
            @JsonProperty("points_required") int pointsRequired,
            boolean affordable) {
    }

    public record GuestPortalBenefitsResponse(
            @JsonProperty("tier_benefits") List<GuestPortalTierBenefit> tierBenefits,
            List<GuestPortalReward> rewards) {
    }

    public record GuestPortalRoomTypeCredit(
            @JsonProperty("room_type_id") long roomTypeId,
            @JsonProperty("room_type_code") String roomTypeCode,
            @JsonProperty("room_type_name") String roomTypeName,
            @JsonProperty("nights_available") int nightsAvailable) {
    }

    public record GuestPortalCreditsResponse(
            @JsonProperty("total_nights_available") int totalNightsAvailable,
            @JsonProperty("credits_by_room_type") List<GuestPortalRoomTypeCredit> creditsByRoomType) {
    }

    // ------------------------------------------------------------------
    // Payments
    // ------------------------------------------------------------------

    public record GuestPaymentConfig(
            @JsonProperty("paypal_enabled") boolean paypalEnabled,
            @JsonProperty("paypal_client_id") String paypalClientId,
            @JsonProperty("bank_details") GuestBankDetails bankDetails) {
    }

    public record GuestBankDetails(
            @JsonProperty("bank_name") String bankName,
            @JsonProperty("account_name") String accountName,
            @JsonProperty("account_number") String accountNumber) {
    }

    public record PaypalCreateOrderResponse(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("payment_id") long paymentId) {
    }

    public record PaypalCaptureRequest(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("payment_id") long paymentId) {
    }

    public record SessionPaypalCaptureRequest(
            @JsonProperty("booking_id") long bookingId,
            @JsonProperty("order_id") String orderId,
            @JsonProperty("payment_id") long paymentId) {
    }

    public record GuestBookingPaymentRequest(@JsonProperty("booking_id") long bookingId) {
    }

    public record PaymentActionResponse(
            @JsonProperty("payment_id") long paymentId,
            String status,
            @JsonProperty("booking_status") String bookingStatus) {
    }
}
