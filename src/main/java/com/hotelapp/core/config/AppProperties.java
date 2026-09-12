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
}
