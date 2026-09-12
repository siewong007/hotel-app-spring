package com.hotelapp.ekyc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import java.time.LocalDate;
import java.util.List;

/** eKYC DTOs mirrored from {@code models/ekyc.rs} (guest-facing subset). */
public final class EkycModels {

    private EkycModels() {
    }

    /** {@code EkycSubmissionRequest}. {@code address} is accepted but unused upstream. */
    public record EkycSubmissionRequest(
            @JsonProperty("selfie_image") String selfieImage,
            @JsonProperty("id_front_image") String idFrontImage,
            @JsonProperty("id_back_image") String idBackImage,
            @JsonProperty("id_type") String idType,
            @JsonProperty("id_number") String idNumber,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("date_of_birth") String dateOfBirth,
            @JsonProperty("nationality") String nationality,
            @JsonProperty("address") String address,
            @JsonProperty("id_expiry_date") String idExpiryDate,
            @JsonProperty("id_issue_date") String idIssueDate,
            @JsonProperty("id_issuing_country") String idIssuingCountry,
            @JsonProperty("proof_of_address") String proofOfAddress,
            @JsonProperty("phone") String phone,
            @JsonProperty("email") String email,
            @JsonProperty("current_address") String currentAddress,
            @JsonProperty("consents") List<ConsentAcceptance> consents) {
    }

    /**
     * {@code EkycStatusResponse} via {@code status_response}: the public
     * projection of the latest verification, {@code verification} always null.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record EkycStatusResponse(
            @JsonProperty("id") long id,
            @JsonProperty("status") String status,
            @JsonProperty("self_checkin_enabled") Boolean selfCheckinEnabled,
            @JsonProperty("submitted_at") Object submittedAt,
            @JsonProperty("verified_at") Object verifiedAt,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("id_type") String idType,
            @JsonProperty("id_expiry_date") LocalDate idExpiryDate,
            @JsonProperty("customer_message") String customerMessage,
            @JsonProperty("verification") Object verification) {
    }
}
