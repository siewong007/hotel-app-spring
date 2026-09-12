package com.hotelapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelapp.auth.dto.AuthDtos.LoginLookupRequest;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.web.ClientTimezone;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the Task-10 auth hardening — mirroring upstream's
 * {@code turnstile.rs}, {@code google_identity.rs} and {@code core/auth.rs}
 * unit cases plus the pure helpers the port added.
 */
class AuthHardeningContractTest {

    // ---- login lookup (non-enumeration) ---------------------------------------

    @Test
    void loginLookupIsConstantForAnyNonEmptyIdentifier() {
        // The service never touches the database — passing null deps proves the
        // response cannot leak whether the identifier is registered.
        AuthService service = new AuthService(null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertThat(service.lookupLoginIdentifier(new LoginLookupRequest("admin")).exists())
                .isTrue();
        assertThat(service.lookupLoginIdentifier(new LoginLookupRequest("ghost@nowhere.example"))
                .exists()).isTrue();
        assertThatThrownBy(
                () -> service.lookupLoginIdentifier(new LoginLookupRequest("   ")))
                .isInstanceOf(ApiError.class);
        assertThatThrownBy(
                () -> service.lookupLoginIdentifier(new LoginLookupRequest(null)))
                .isInstanceOf(ApiError.class);
    }

    // ---- Turnstile failure classification ---------------------------------------

    @Test
    void badSecretIsServiceUnavailableNotBadRequest() {
        assertThatThrownBy(
                () -> Turnstile.classifyFailure(List.of("invalid-input-secret"), "login"))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).kind()).isEqualTo(ApiError.Kind.SERVICE_UNAVAILABLE);
    }

    @Test
    void missingSecretIsServiceUnavailable() {
        assertThatThrownBy(
                () -> Turnstile.classifyFailure(List.of("missing-input-secret"), "register"))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).kind()).isEqualTo(ApiError.Kind.SERVICE_UNAVAILABLE);
    }

    @Test
    void invalidTokenIsBadRequest() {
        assertThatThrownBy(
                () -> Turnstile.classifyFailure(List.of("invalid-input-response"), "login"))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).kind()).isEqualTo(ApiError.Kind.BAD_REQUEST);
    }

    @Test
    void replayedTokenIsBadRequest() {
        assertThatThrownBy(
                () -> Turnstile.classifyFailure(List.of("timeout-or-duplicate"), "login"))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).kind()).isEqualTo(ApiError.Kind.BAD_REQUEST);
    }

    @Test
    void emptyCodeListStillFails() {
        assertThatThrownBy(() -> Turnstile.classifyFailure(List.of(), "login"))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).kind()).isEqualTo(ApiError.Kind.BAD_REQUEST);
    }

    @Test
    void operatorFaultWinsOverVisitorFault() {
        assertThatThrownBy(() -> Turnstile.classifyFailure(
                List.of("invalid-input-response", "invalid-input-secret"), "login"))
                .isInstanceOf(ApiError.class)
                .extracting(e -> ((ApiError) e).kind()).isEqualTo(ApiError.Kind.SERVICE_UNAVAILABLE);
    }

    // ---- Google identity naming + completion ------------------------------------

    @Test
    void googleUsernameIsLowercaseAndDatabaseSafe() {
        String username = GoogleIdentityService.usernameFor(
                "Aisha.Rahman@gmail.com", "10987654321");
        assertThat(username).startsWith("aisha_rahman_");
        assertThat(username.length()).isLessThanOrEqualTo(100);
        assertThat(username.chars().allMatch(c ->
                (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-'))
                .isTrue();
    }

    @Test
    void googleUsernamesDoNotCollideForDifferentSubjects() {
        String first = GoogleIdentityService.usernameFor(
                "aisha.rahman@example.com", "first-subject-654321");
        String second = GoogleIdentityService.usernameFor(
                "aisha.rahman@example.com", "second-subject-654321");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void profileCompletionRequiresFirstNameLastNameAndPhone() {
        assertThat(GoogleIdentityService.profileCompletion("Aisha", "Rahman", null)
                .missingFields()).containsExactly("phone");
    }

    @Test
    void profileCompletionDoesNotRequireAnAddress() {
        assertThat(GoogleIdentityService
                .profileCompletion("Aisha", "Rahman", "+60123456789").complete()).isTrue();
    }

    @Test
    void googleDisplayNameFallsBackToGuest() {
        var identity = new GoogleIdentityService.Identity("sub", "e@example.com", null, null);
        assertThat(GoogleIdentityService.displayName(identity)).isEqualTo("Google guest");
        var named = new GoogleIdentityService.Identity("sub", "e@example.com",
                "Aisha", "Rahman");
        assertThat(GoogleIdentityService.displayName(named)).isEqualTo("Aisha Rahman");
    }

    // ---- TOTP secret sealing + recovery codes -------------------------------------

    @Test
    void totpSealIsIdentityWithoutAKey() {
        TotpSecrets secrets = new TotpSecrets(noKeyProperties());
        assertThat(secrets.seal("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(secrets.open("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void totpSealOpenRoundTripsWithBase64Key() {
        TotpSecrets secrets = new TotpSecrets(keyedProperties());
        String sealed = secrets.seal("JBSWY3DPEHPK3PXP");
        assertThat(sealed).startsWith("enc1:");
        assertThat(secrets.open(sealed)).isEqualTo("JBSWY3DPEHPK3PXP");
        // A second seal produces a different nonce — sealing is non-deterministic.
        assertThat(secrets.seal("JBSWY3DPEHPK3PXP")).isNotEqualTo(sealed);
    }

    @Test
    void totpOpenRejectsSealedSecretWithoutKey() {
        TotpSecrets sealed = new TotpSecrets(keyedProperties());
        String value = sealed.seal("JBSWY3DPEHPK3PXP");
        TotpSecrets unkeyed = new TotpSecrets(noKeyProperties());
        assertThatThrownBy(() -> unkeyed.open(value))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void totpOpenRejectsTamperedSealedSecret() {
        TotpSecrets secrets = new TotpSecrets(keyedProperties());
        String sealed = secrets.seal("JBSWY3DPEHPK3PXP");
        // Flip the last byte of the payload — the GCM tag must reject it.
        byte[] payload = java.util.Base64.getUrlDecoder().decode(sealed.substring(5));
        payload[payload.length - 1] ^= 0x01;
        String tampered = "enc1:"
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        assertThatThrownBy(() -> secrets.open(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recoveryCodesAreHashedForStorageAndCheckable() {
        TotpSecrets secrets = new TotpSecrets(noKeyProperties());
        List<String> codes = secrets.generateBackupCodes();
        assertThat(codes).hasSize(10);
        assertThat(codes.get(0)).matches("[0-9A-F]{5}-[0-9A-F]{5}-[0-9A-F]{5}-[0-9A-F]{5}");
        List<String> stored = secrets.recoveryCodesForStorage(codes);
        // Stored values are 64-hex hashes, not the codes themselves.
        assertThat(stored.get(0)).matches("[0-9a-f]{64}").isNotEqualTo(codes.get(0));
        Integer index = TotpSecrets.checkRecoveryCode(codes.get(3), stored);
        assertThat(index).isEqualTo(3);
        assertThat(TotpSecrets.checkRecoveryCode("AAAAA-BBBBB-CCCCC-DDDDD", stored)).isNull();
    }

    // ---- password complexity -----------------------------------------------------

    @Test
    void passwordRulesRejectWeakAndAcceptStrong() {
        assertThatThrownBy(() -> AuthService.validatePassword("short"))
                .isInstanceOf(ApiError.class);
        assertThatThrownBy(() -> AuthService.validatePassword("alllowercase1!"))
                .isInstanceOf(ApiError.class);
        assertThatThrownBy(() -> AuthService.validatePassword("ALLUPPER1!"))
                .isInstanceOf(ApiError.class);
        assertThatThrownBy(() -> AuthService.validatePassword("NoDigits!"))
                .isInstanceOf(ApiError.class);
        assertThatThrownBy(() -> AuthService.validatePassword("NoSpecial1"))
                .isInstanceOf(ApiError.class);
        assertThatThrownBy(() -> AuthService.validatePassword("Password123!"))
                .isInstanceOf(ApiError.class); // contains "password"
        AuthService.validatePassword("S3cure_Rooms!");
    }

    // ---- client timezone sanitize ---------------------------------------------------

    @Test
    void clientTimezoneAcceptsIanaShapesAndDropsTheRest() {
        assertThat(ClientTimezone.sanitize("Asia/Kuala_Lumpur")).isEqualTo("Asia/Kuala_Lumpur");
        assertThat(ClientTimezone.sanitize("UTC")).isEqualTo("UTC");
        assertThat(ClientTimezone.sanitize("America/Argentina/Salta"))
                .isEqualTo("America/Argentina/Salta");
        assertThat(ClientTimezone.sanitize("  Asia/Tokyo  ")).isEqualTo("Asia/Tokyo");
        assertThat(ClientTimezone.sanitize("")).isNull();
        assertThat(ClientTimezone.sanitize("   ")).isNull();
        assertThat(ClientTimezone.sanitize(null)).isNull();
        assertThat(ClientTimezone.sanitize("a/b/c/d")).isNull();      // 4 segments
        assertThat(ClientTimezone.sanitize("a//b")).isNull();         // empty segment
        assertThat(ClientTimezone.sanitize("x".repeat(65))).isNull(); // > 64
        assertThat(ClientTimezone.sanitize("Area/City!<script>")).isNull();
        assertThat(ClientTimezone.sanitize("Area/City Name")).isNull(); // space
    }

    // ---- profile helpers -------------------------------------------------------------

    @Test
    void locationFromTimezoneDerivesCityAndSkipsMachineZones() {
        assertThat(com.hotelapp.profile.ProfileService.locationFromTimezone(
                "Asia/Kuala_Lumpur")).isEqualTo("Kuala Lumpur");
        assertThat(com.hotelapp.profile.ProfileService.locationFromTimezone(
                "America/Argentina/Salta")).isEqualTo("Salta");
        assertThat(com.hotelapp.profile.ProfileService.locationFromTimezone("UTC")).isNull();
        assertThat(com.hotelapp.profile.ProfileService.locationFromTimezone("Etc/GMT+8")).isNull();
    }

    @Test
    void maskIpAddressHidesLastSegment() {
        assertThat(com.hotelapp.profile.ProfileService.maskIpAddress("203.0.113.45"))
                .isEqualTo("203.0.113.•••");
        assertThat(com.hotelapp.profile.ProfileService.maskIpAddress("2001:db8::ff00:42:8329"))
                .isEqualTo("2001:db8::ff00:42:••••");
        assertThat(com.hotelapp.profile.ProfileService.maskIpAddress("opaque")).isEqualTo("•••");
    }

    // ---- helpers -------------------------------------------------------------------

    private static com.hotelapp.core.config.AppProperties noKeyProperties() {
        var props = new com.hotelapp.core.config.AppProperties();
        props.setTotpEncryptionKey("");
        return props;
    }

    private static com.hotelapp.core.config.AppProperties keyedProperties() {
        var props = new com.hotelapp.core.config.AppProperties();
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 0x42);
        props.setTotpEncryptionKey(java.util.Base64.getEncoder().encodeToString(key));
        return props;
    }
}
