package com.hotelapp.core.security;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.error.ApiErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SessionBoundAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ActiveSessionValidator sessionValidator;

    public SessionBoundAuthFilter(JwtService jwtService, ActiveSessionValidator sessionValidator) {
        this.jwtService = jwtService;
        this.sessionValidator = sessionValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthClaims claims;
        try {
            claims = jwtService.parse(header.substring(7));
        } catch (JwtValidationException e) {
            if (request.getRequestURI().contains("/guest-portal/")) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            ApiErrorResponses.write(ApiError.unauthorized("Invalid or expired token"), response);
            return;
        }

        if (claims.sid() == null) {
            ApiErrorResponses
                    .write(ApiError.unauthorized("Session-bound authentication is required"), response);
            return;
        }
        long userId = parseUserId(claims);
        if (userId < 0) {
            ApiErrorResponses.write(ApiError.unauthorized("Invalid user ID in token"), response);
            return;
        }
        boolean active;
        try {
            active = sessionValidator.isActive(userId, claims.sid());
        } catch (Exception e) {
            ApiErrorResponses.write(
                    ApiError.database("Session validation failed: " + e.getMessage()), response);
            return;
        }
        if (!active) {
            ApiErrorResponses.write(ApiError.unauthorized("Session has been logged out"), response);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(userId, claims),
                        null, List.of()));
        filterChain.doFilter(request, response);
    }

    private long parseUserId(AuthClaims claims) {
        try {
            return Long.parseLong(claims.sub());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
