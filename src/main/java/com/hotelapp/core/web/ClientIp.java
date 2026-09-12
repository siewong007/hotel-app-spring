package com.hotelapp.core.web;

import com.hotelapp.core.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Port of routes/mod.rs extract_client_ip: when proxy headers are trusted the
 * RIGHTMOST X-Forwarded-For entry wins — it is the only entry this deployment's
 * proxy added; leftmost is client-controlled and spoofable.
 */
@Component
public class ClientIp {

    private final AppProperties properties;

    public ClientIp(AppProperties properties) {
        this.properties = properties;
    }

    public String extract(HttpServletRequest request) {
        if (!properties.isTrustProxyHeaders()) {
            return request.getRemoteAddr();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null) {
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.trim().isEmpty()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
