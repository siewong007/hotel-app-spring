package com.hotelapp.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String jwtSecret;
    private String environment;
    private boolean desktopMode;
    private String allowedOriginsRaw;
    private boolean trustProxyHeaders;

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
