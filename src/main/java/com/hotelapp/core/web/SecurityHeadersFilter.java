package com.hotelapp.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String STRICT_TRANSPORT_SECURITY = "max-age=31536000; includeSubDomains";
    static final String X_CONTENT_TYPE_OPTIONS = "nosniff";
    static final String X_FRAME_OPTIONS = "DENY";
    static final String X_XSS_PROTECTION = "1; mode=block";
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self' https://*.paypal.com"
                    + " https://*.paypalobjects.com https://*.venmo.com; style-src 'self'"
                    + " https://*.paypal.com https://*.paypalobjects.com https://*.venmo.com;"
                    + " img-src 'self' data: https:; font-src 'self' data:;"
                    + " connect-src 'self' https://*.paypal.com https://*.paypalobjects.com"
                    + " https://*.venmo.com; frame-src 'self' https://*.paypal.com"
                    + " https://*.paypalobjects.com https://*.venmo.com; frame-ancestors"
                    + " 'none';";
    static final String REFERRER_POLICY = "strict-origin-when-cross-origin";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        addHeaderIfAbsent(response, "Strict-Transport-Security", STRICT_TRANSPORT_SECURITY);
        addHeaderIfAbsent(response, "X-Content-Type-Options", X_CONTENT_TYPE_OPTIONS);
        addHeaderIfAbsent(response, "X-Frame-Options", X_FRAME_OPTIONS);
        addHeaderIfAbsent(response, "X-XSS-Protection", X_XSS_PROTECTION);
        addHeaderIfAbsent(response, "Content-Security-Policy", CONTENT_SECURITY_POLICY);
        addHeaderIfAbsent(response, "Referrer-Policy", REFERRER_POLICY);
        filterChain.doFilter(request, response);
    }

    private void addHeaderIfAbsent(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}
