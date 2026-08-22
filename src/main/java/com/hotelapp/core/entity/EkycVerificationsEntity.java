package com.hotelapp.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "ekyc_verifications",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"uuid"})})
public class EkycVerificationsEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private java.util.UUID uuid;

    @Column(name = "user_id")
    private Long user_id;

    @Column(name = "guest_id")
    private Long guest_id;

    @Column(name = "status", columnDefinition = "varchar(50)")
    private String status;

    @Column(name = "assigned_reviewer_id")
    private Long assigned_reviewer_id;

    @Column(name = "reviewer_claimed_at")
    private java.time.OffsetDateTime reviewer_claimed_at;

    @Column(name = "full_name", columnDefinition = "varchar(255)")
    private String full_name;

    @Column(name = "date_of_birth")
    private java.time.LocalDate date_of_birth;

    @Column(name = "nationality", columnDefinition = "varchar(100)")
    private String nationality;

    @Column(name = "phone", columnDefinition = "varchar(50)")
    private String phone;

    @Column(name = "email", columnDefinition = "varchar(255)")
    private String email;

    @Column(name = "current_address", columnDefinition = "text")
    private String current_address;

    @Column(name = "id_type", columnDefinition = "varchar(80)")
    private String id_type;

    @Column(name = "id_number", columnDefinition = "varchar(255)")
    private String id_number;

    @Column(name = "id_issuing_country", columnDefinition = "varchar(100)")
    private String id_issuing_country;

    @Column(name = "id_issue_date")
    private java.time.LocalDate id_issue_date;

    @Column(name = "id_expiry_date")
    private java.time.LocalDate id_expiry_date;

    @Column(name = "id_front_image_path", columnDefinition = "text")
    private String id_front_image_path;

    @Column(name = "id_back_image_path", columnDefinition = "text")
    private String id_back_image_path;

    @Column(name = "selfie_image_path", columnDefinition = "text")
    private String selfie_image_path;

    @Column(name = "proof_of_address_path", columnDefinition = "text")
    private String proof_of_address_path;

    @Column(name = "provider_name", columnDefinition = "varchar(100)")
    private String provider_name;

    @Column(name = "provider_verification_result", columnDefinition = "varchar(80)")
    private String provider_verification_result;

    @Column(name = "provider_raw_response")
    private String provider_raw_response;

    @Column(name = "ocr_data")
    private String ocr_data;

    @Column(name = "user_entered_data")
    private String user_entered_data;

    @Column(name = "document_authenticity_result", columnDefinition = "varchar(80)")
    private String document_authenticity_result;

    @Column(name = "face_match_score")
    private Double face_match_score;

    @Column(name = "face_match_passed")
    private Boolean face_match_passed;

    @Column(name = "liveness_score")
    private Double liveness_score;

    @Column(name = "liveness_passed")
    private Boolean liveness_passed;

    @Column(name = "duplicate_check_result", columnDefinition = "varchar(80)")
    private String duplicate_check_result;

    @Column(name = "watchlist_result", columnDefinition = "varchar(80)")
    private String watchlist_result;

    @Column(name = "ip_address", columnDefinition = "varchar(64)")
    private String ip_address;

    @Column(name = "device_fingerprint", columnDefinition = "varchar(255)")
    private String device_fingerprint;

    @Column(name = "geolocation", columnDefinition = "varchar(255)")
    private String geolocation;

    @Column(name = "submission_metadata")
    private String submission_metadata;

    @Column(name = "auto_verified")
    private Boolean auto_verified;

    @Column(name = "auto_verification_details")
    private String auto_verification_details;

    @Column(name = "manual_review_required")
    private Boolean manual_review_required;

    @Column(name = "risk_level", columnDefinition = "varchar(30)")
    private String risk_level;

    @Column(name = "risk_score")
    private Integer risk_score;

    @Column(name = "risk_flags")
    private String risk_flags;

    @Column(name = "recommended_action", columnDefinition = "varchar(100)")
    private String recommended_action;

    @Column(name = "potential_duplicate")
    private Boolean potential_duplicate;

    @Column(name = "fraud_suspected")
    private Boolean fraud_suspected;

    @Column(name = "verification_notes", columnDefinition = "text")
    private String verification_notes;

    @Column(name = "customer_message", columnDefinition = "text")
    private String customer_message;

    @Column(name = "decision_reason_code", columnDefinition = "varchar(80)")
    private String decision_reason_code;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decision_reason;

    @Column(name = "verified_by")
    private Long verified_by;

    @Column(name = "verified_at")
    private java.time.OffsetDateTime verified_at;

    @Column(name = "self_checkin_enabled")
    private Boolean self_checkin_enabled;

    @Column(name = "self_checkin_activated_at")
    private java.time.OffsetDateTime self_checkin_activated_at;

    @Column(name = "submitted_at")
    private java.time.OffsetDateTime submitted_at;

    @Column(name = "version")
    private Integer version;

    @Column(name = "created_at")
    private java.time.OffsetDateTime created_at;

    @Column(name = "updated_at")
    private java.time.OffsetDateTime updated_at;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public java.util.UUID getUuid() { return uuid; }
    public void setUuid(java.util.UUID uuid) { this.uuid = uuid; }

    public Long getUserId() { return user_id; }
    public void setUserId(Long user_id) { this.user_id = user_id; }

    public Long getGuestId() { return guest_id; }
    public void setGuestId(Long guest_id) { this.guest_id = guest_id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAssignedReviewerId() { return assigned_reviewer_id; }
    public void setAssignedReviewerId(Long assigned_reviewer_id) { this.assigned_reviewer_id = assigned_reviewer_id; }

    public java.time.OffsetDateTime getReviewerClaimedAt() { return reviewer_claimed_at; }
    public void setReviewerClaimedAt(java.time.OffsetDateTime reviewer_claimed_at) { this.reviewer_claimed_at = reviewer_claimed_at; }

    public String getFullName() { return full_name; }
    public void setFullName(String full_name) { this.full_name = full_name; }

    public java.time.LocalDate getDateOfBirth() { return date_of_birth; }
    public void setDateOfBirth(java.time.LocalDate date_of_birth) { this.date_of_birth = date_of_birth; }

    public String getNationality() { return nationality; }
    public void setNationality(String nationality) { this.nationality = nationality; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCurrentAddress() { return current_address; }
    public void setCurrentAddress(String current_address) { this.current_address = current_address; }

    public String getIdType() { return id_type; }
    public void setIdType(String id_type) { this.id_type = id_type; }

    public String getIdNumber() { return id_number; }
    public void setIdNumber(String id_number) { this.id_number = id_number; }

    public String getIdIssuingCountry() { return id_issuing_country; }
    public void setIdIssuingCountry(String id_issuing_country) { this.id_issuing_country = id_issuing_country; }

    public java.time.LocalDate getIdIssueDate() { return id_issue_date; }
    public void setIdIssueDate(java.time.LocalDate id_issue_date) { this.id_issue_date = id_issue_date; }

    public java.time.LocalDate getIdExpiryDate() { return id_expiry_date; }
    public void setIdExpiryDate(java.time.LocalDate id_expiry_date) { this.id_expiry_date = id_expiry_date; }

    public String getIdFrontImagePath() { return id_front_image_path; }
    public void setIdFrontImagePath(String id_front_image_path) { this.id_front_image_path = id_front_image_path; }

    public String getIdBackImagePath() { return id_back_image_path; }
    public void setIdBackImagePath(String id_back_image_path) { this.id_back_image_path = id_back_image_path; }

    public String getSelfieImagePath() { return selfie_image_path; }
    public void setSelfieImagePath(String selfie_image_path) { this.selfie_image_path = selfie_image_path; }

    public String getProofOfAddressPath() { return proof_of_address_path; }
    public void setProofOfAddressPath(String proof_of_address_path) { this.proof_of_address_path = proof_of_address_path; }

    public String getProviderName() { return provider_name; }
    public void setProviderName(String provider_name) { this.provider_name = provider_name; }

    public String getProviderVerificationResult() { return provider_verification_result; }
    public void setProviderVerificationResult(String provider_verification_result) { this.provider_verification_result = provider_verification_result; }

    public String getProviderRawResponse() { return provider_raw_response; }
    public void setProviderRawResponse(String provider_raw_response) { this.provider_raw_response = provider_raw_response; }

    public String getOcrData() { return ocr_data; }
    public void setOcrData(String ocr_data) { this.ocr_data = ocr_data; }

    public String getUserEnteredData() { return user_entered_data; }
    public void setUserEnteredData(String user_entered_data) { this.user_entered_data = user_entered_data; }

    public String getDocumentAuthenticityResult() { return document_authenticity_result; }
    public void setDocumentAuthenticityResult(String document_authenticity_result) { this.document_authenticity_result = document_authenticity_result; }

    public Double getFaceMatchScore() { return face_match_score; }
    public void setFaceMatchScore(Double face_match_score) { this.face_match_score = face_match_score; }

    public Boolean isFaceMatchPassed() { return face_match_passed; }
    public void setFaceMatchPassed(Boolean face_match_passed) { this.face_match_passed = face_match_passed; }

    public Double getLivenessScore() { return liveness_score; }
    public void setLivenessScore(Double liveness_score) { this.liveness_score = liveness_score; }

    public Boolean isLivenessPassed() { return liveness_passed; }
    public void setLivenessPassed(Boolean liveness_passed) { this.liveness_passed = liveness_passed; }

    public String getDuplicateCheckResult() { return duplicate_check_result; }
    public void setDuplicateCheckResult(String duplicate_check_result) { this.duplicate_check_result = duplicate_check_result; }

    public String getWatchlistResult() { return watchlist_result; }
    public void setWatchlistResult(String watchlist_result) { this.watchlist_result = watchlist_result; }

    public String getIpAddress() { return ip_address; }
    public void setIpAddress(String ip_address) { this.ip_address = ip_address; }

    public String getDeviceFingerprint() { return device_fingerprint; }
    public void setDeviceFingerprint(String device_fingerprint) { this.device_fingerprint = device_fingerprint; }

    public String getGeolocation() { return geolocation; }
    public void setGeolocation(String geolocation) { this.geolocation = geolocation; }

    public String getSubmissionMetadata() { return submission_metadata; }
    public void setSubmissionMetadata(String submission_metadata) { this.submission_metadata = submission_metadata; }

    public Boolean isAutoVerified() { return auto_verified; }
    public void setAutoVerified(Boolean auto_verified) { this.auto_verified = auto_verified; }

    public String getAutoVerificationDetails() { return auto_verification_details; }
    public void setAutoVerificationDetails(String auto_verification_details) { this.auto_verification_details = auto_verification_details; }

    public Boolean isManualReviewRequired() { return manual_review_required; }
    public void setManualReviewRequired(Boolean manual_review_required) { this.manual_review_required = manual_review_required; }

    public String getRiskLevel() { return risk_level; }
    public void setRiskLevel(String risk_level) { this.risk_level = risk_level; }

    public Integer getRiskScore() { return risk_score; }
    public void setRiskScore(Integer risk_score) { this.risk_score = risk_score; }

    public String getRiskFlags() { return risk_flags; }
    public void setRiskFlags(String risk_flags) { this.risk_flags = risk_flags; }

    public String getRecommendedAction() { return recommended_action; }
    public void setRecommendedAction(String recommended_action) { this.recommended_action = recommended_action; }

    public Boolean isPotentialDuplicate() { return potential_duplicate; }
    public void setPotentialDuplicate(Boolean potential_duplicate) { this.potential_duplicate = potential_duplicate; }

    public Boolean isFraudSuspected() { return fraud_suspected; }
    public void setFraudSuspected(Boolean fraud_suspected) { this.fraud_suspected = fraud_suspected; }

    public String getVerificationNotes() { return verification_notes; }
    public void setVerificationNotes(String verification_notes) { this.verification_notes = verification_notes; }

    public String getCustomerMessage() { return customer_message; }
    public void setCustomerMessage(String customer_message) { this.customer_message = customer_message; }

    public String getDecisionReasonCode() { return decision_reason_code; }
    public void setDecisionReasonCode(String decision_reason_code) { this.decision_reason_code = decision_reason_code; }

    public String getDecisionReason() { return decision_reason; }
    public void setDecisionReason(String decision_reason) { this.decision_reason = decision_reason; }

    public Long getVerifiedBy() { return verified_by; }
    public void setVerifiedBy(Long verified_by) { this.verified_by = verified_by; }

    public java.time.OffsetDateTime getVerifiedAt() { return verified_at; }
    public void setVerifiedAt(java.time.OffsetDateTime verified_at) { this.verified_at = verified_at; }

    public Boolean isSelfCheckinEnabled() { return self_checkin_enabled; }
    public void setSelfCheckinEnabled(Boolean self_checkin_enabled) { this.self_checkin_enabled = self_checkin_enabled; }

    public java.time.OffsetDateTime getSelfCheckinActivatedAt() { return self_checkin_activated_at; }
    public void setSelfCheckinActivatedAt(java.time.OffsetDateTime self_checkin_activated_at) { this.self_checkin_activated_at = self_checkin_activated_at; }

    public java.time.OffsetDateTime getSubmittedAt() { return submitted_at; }
    public void setSubmittedAt(java.time.OffsetDateTime submitted_at) { this.submitted_at = submitted_at; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public java.time.OffsetDateTime getCreatedAt() { return created_at; }
    public void setCreatedAt(java.time.OffsetDateTime created_at) { this.created_at = created_at; }

    public java.time.OffsetDateTime getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(java.time.OffsetDateTime updated_at) { this.updated_at = updated_at; }
}
