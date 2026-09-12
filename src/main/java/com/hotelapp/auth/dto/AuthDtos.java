package com.hotelapp.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotelapp.portal.PortalModels.ConsentAcceptance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Request/response records for the auth, 2FA, passkey and profile surfaces —
 * port of {@code models/auth.rs} and the profile slice of {@code models/user.rs}.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // ---- login lookup -----------------------------------------------------

    public record LoginLookupRequest(
            @NotBlank(message = "Username or email is required") String username) {
    }

    public record LoginLookupResponse(boolean exists) {
    }

    // ---- Google One Tap ---------------------------------------------------

    public record GoogleLoginRequest(
            @NotBlank(message = "Google credential is required") String credential,
            List<ConsentAcceptance> consents,
            @JsonProperty("marketing_opt_in") boolean marketingOptIn) {
    }

    // ---- registration -----------------------------------------------------

    public record RegisterRequest(
            String username,
            String email,
            String password,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String phone,
            @JsonProperty("address_line1") String addressLine1,
            List<ConsentAcceptance> consents,
            @JsonProperty("marketing_opt_in") boolean marketingOptIn) {
    }

    public record EmailVerificationConfirm(String token) {
    }

    public record ResendVerificationRequest(String email) {
    }

    // ---- two-factor -------------------------------------------------------

    public record TwoFactorEnableRequest(
            @Size(min = 6, max = 12, message = "Invalid 2FA code") String code,
            @JsonProperty("challenge_code")
            @Size(min = 64, max = 64, message = "Invalid challenge code") String challengeCode) {
    }

    public record TwoFactorDisableRequest(
            @Size(min = 6, max = 32, message = "Invalid 2FA code") String code) {
    }

    public record TwoFactorVerifyRequest(
            @Size(min = 6, max = 20, message = "Invalid 2FA code") String code) {
    }

    public record RegenerateBackupCodesRequest(
            @Size(min = 6, max = 20, message = "Invalid 2FA code") String code) {
    }

    public record TwoFactorStatusResponse(
            boolean enabled,
            @JsonProperty("has_backup_codes") boolean hasBackupCodes,
            @JsonProperty("backup_codes_remaining") int backupCodesRemaining,
            @JsonProperty("backup_codes_generated_at") Instant backupCodesGeneratedAt) {
    }

    // ---- passkeys ---------------------------------------------------------

    public record PasskeyRegistrationStart(
            String username, String password, @JsonProperty("totp_code") String totpCode) {
    }

    public record PasskeyRegistrationFinish(
            String username, String credential, String challenge,
            @JsonProperty("device_name") String deviceName) {
    }

    public record PasskeyLoginStart(String username) {
    }

    public record PasskeyLoginFinish(
            String username,
            @JsonProperty("credential_id") String credentialId,
            @JsonProperty("authenticator_data") String authenticatorData,
            @JsonProperty("client_data_json") String clientDataJson,
            String signature,
            String challenge) {
    }

    public record PasskeyInfo(
            String id,
            @JsonProperty("credential_id") String credentialId,
            @JsonProperty("device_name") String deviceName,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("last_used_at") Instant lastUsedAt) {
    }

    public record PasskeyUpdateInput(@JsonProperty("device_name") String deviceName) {
    }

    // ---- profile ----------------------------------------------------------

    public record UserProfile(
            long id,
            String username,
            String email,
            @JsonProperty("email_configured") boolean emailConfigured,
            @JsonProperty("is_verified") boolean isVerified,
            @JsonProperty("user_type") String userType,
            @JsonProperty("full_name") String fullName,
            String phone,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("last_login_at") Instant lastLoginAt,
            @JsonProperty("profile_complete") boolean profileComplete,
            @JsonProperty("missing_profile_fields") List<String> missingProfileFields) {
    }

    public record UserProfileUpdate(
            @JsonProperty("full_name") String fullName,
            String email,
            String phone,
            @JsonProperty("avatar_url") String avatarUrl) {
    }

    public record PasswordUpdateInput(
            @JsonProperty("current_password") String currentPassword,
            @JsonProperty("new_password") String newPassword) {
    }

    public record CompleteGuestProfileRequest(
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            String phone,
            @JsonProperty("address_line1") String addressLine1) {
    }

    public record UserSessionInfo(
            String id,
            @JsonProperty("user_agent") String userAgent,
            @JsonProperty("ip_address") String ipAddress,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("last_used_at") Instant lastUsedAt,
            @JsonProperty("expires_at") Instant expiresAt,
            @JsonProperty("is_current") boolean isCurrent,
            String location,
            String timezone) {
    }
}
