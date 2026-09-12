package com.hotelapp.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String jwtSecret;
    private String jwtIssuer = "hotel-app-be";
    private String jwtAudience = "hotel-web";
    private String environment;
    private boolean desktopMode;
    private String allowedOriginsRaw;
    private boolean trustProxyHeaders;

    private boolean turnstileEnabled;
    private String turnstileSiteKey;
    private String turnstileSecretKey;
    private String turnstileVerifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private String googleClientId;
    private String passkeyRpId = "localhost";
    private String totpEncryptionKey;
    private boolean skipEmailVerification;
    private String publicBaseUrl = "http://localhost:3000";
    private String smtpFromName = "Salim Inn";

    private boolean paypalEnabled;
    private String paypalClientId;
    private String paypalClientSecret;
    private String paypalApiBase = "https://api-m.sandbox.paypal.com";
    private String paypalWebhookId;

    private String hotelBankName = "Maybank";
    private String hotelBankAccountName = "Salim Inn";
    private String hotelBankAccountNumber = "511270052595";

    /** Mirrors upstream PaypalConfig::is_configured. */
    public boolean isPaypalConfigured() {
        return paypalEnabled
                && paypalClientId != null && !paypalClientId.isBlank()
                && paypalClientSecret != null && !paypalClientSecret.isBlank();
    }

    /** Public by design; only exposed while the integration is configured. */
    public String paypalPublicClientId() {
        return isPaypalConfigured() ? paypalClientId : null;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getSmtpFromName() {
        return smtpFromName;
    }

    public void setSmtpFromName(String smtpFromName) {
        this.smtpFromName = smtpFromName;
    }

    public boolean isPaypalEnabled() {
        return paypalEnabled;
    }

    public void setPaypalEnabled(boolean paypalEnabled) {
        this.paypalEnabled = paypalEnabled;
    }

    public String getPaypalClientId() {
        return paypalClientId;
    }

    public void setPaypalClientId(String paypalClientId) {
        this.paypalClientId = paypalClientId;
    }

    public String getPaypalClientSecret() {
        return paypalClientSecret;
    }

    public void setPaypalClientSecret(String paypalClientSecret) {
        this.paypalClientSecret = paypalClientSecret;
    }

    public String getPaypalApiBase() {
        return paypalApiBase;
    }

    public void setPaypalApiBase(String paypalApiBase) {
        this.paypalApiBase = paypalApiBase == null ? null : paypalApiBase.replaceAll("/+$", "");
    }

    public String getPaypalWebhookId() {
        return paypalWebhookId;
    }

    public void setPaypalWebhookId(String paypalWebhookId) {
        this.paypalWebhookId = paypalWebhookId;
    }

    public String getHotelBankName() {
        return hotelBankName;
    }

    public void setHotelBankName(String hotelBankName) {
        this.hotelBankName = hotelBankName;
    }

    public String getHotelBankAccountName() {
        return hotelBankAccountName;
    }

    public void setHotelBankAccountName(String hotelBankAccountName) {
        this.hotelBankAccountName = hotelBankAccountName;
    }

    public String getHotelBankAccountNumber() {
        return hotelBankAccountNumber;
    }

    public void setHotelBankAccountNumber(String hotelBankAccountNumber) {
        this.hotelBankAccountNumber = hotelBankAccountNumber;
    }

    public String getJwtIssuer() {
        return jwtIssuer;
    }

    public void setJwtIssuer(String jwtIssuer) {
        this.jwtIssuer = jwtIssuer;
    }

    public String getJwtAudience() {
        return jwtAudience;
    }

    public void setJwtAudience(String jwtAudience) {
        this.jwtAudience = jwtAudience;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public boolean isDesktopMode() {
        return desktopMode;
    }

    public void setDesktopMode(boolean desktopMode) {
        this.desktopMode = desktopMode;
    }

    public String getAllowedOriginsRaw() {
        return allowedOriginsRaw;
    }

    public void setAllowedOriginsRaw(String allowedOriginsRaw) {
        this.allowedOriginsRaw = allowedOriginsRaw;
    }

    public boolean isTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }

    /** Mirrors TurnstileConfig::trimmed. */
    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    /** Mirrors TurnstileConfig::keys_are_identical. */
    public boolean turnstileKeysAreIdentical() {
        String site = trimmed(turnstileSiteKey);
        String secret = trimmed(turnstileSecretKey);
        return site != null && site.equals(secret);
    }

    /** Mirrors TurnstileConfig::is_configured — on AND both distinct keys. */
    public boolean isTurnstileConfigured() {
        return turnstileEnabled
                && trimmed(turnstileSiteKey) != null
                && trimmed(turnstileSecretKey) != null
                && !turnstileKeysAreIdentical();
    }

    /** Mirrors TurnstileConfig::active_secret. */
    public String turnstileActiveSecret() {
        return isTurnstileConfigured() ? trimmed(turnstileSecretKey) : null;
    }

    public boolean isTurnstileEnabled() {
        return turnstileEnabled;
    }

    public void setTurnstileEnabled(boolean turnstileEnabled) {
        this.turnstileEnabled = turnstileEnabled;
    }

    public String getTurnstileSiteKey() {
        return turnstileSiteKey;
    }

    public void setTurnstileSiteKey(String turnstileSiteKey) {
        this.turnstileSiteKey = turnstileSiteKey;
    }

    public String getTurnstileSecretKey() {
        return turnstileSecretKey;
    }

    public void setTurnstileSecretKey(String turnstileSecretKey) {
        this.turnstileSecretKey = turnstileSecretKey;
    }

    public String getTurnstileVerifyUrl() {
        return turnstileVerifyUrl;
    }

    public void setTurnstileVerifyUrl(String turnstileVerifyUrl) {
        this.turnstileVerifyUrl = turnstileVerifyUrl;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public void setGoogleClientId(String googleClientId) {
        this.googleClientId = googleClientId;
    }

    public String getPasskeyRpId() {
        return passkeyRpId;
    }

    public void setPasskeyRpId(String passkeyRpId) {
        this.passkeyRpId = passkeyRpId;
    }

    public String getTotpEncryptionKey() {
        return totpEncryptionKey;
    }

    public void setTotpEncryptionKey(String totpEncryptionKey) {
        this.totpEncryptionKey = totpEncryptionKey;
    }

    public boolean isSkipEmailVerification() {
        return skipEmailVerification;
    }

    public void setSkipEmailVerification(boolean skipEmailVerification) {
        this.skipEmailVerification = skipEmailVerification;
    }
}
