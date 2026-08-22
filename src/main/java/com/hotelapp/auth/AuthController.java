package com.hotelapp.auth;

import com.hotelapp.auth.dto.AccessSnapshot;
import com.hotelapp.auth.dto.AuthResponse;
import com.hotelapp.auth.dto.LoginRequest;
import com.hotelapp.auth.dto.RefreshTokenResponse;
import com.hotelapp.core.error.ApiErrorResponses;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimiter;
    private final boolean secureCookies;

    public AuthController(AuthService authService, RateLimitService rateLimiter,
            @Value("${app.environment:development}") String environment) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.secureCookies = "production".equalsIgnoreCase(environment);
    }

    @PostMapping(value = "/api/auth/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String ip = clientIp(httpRequest);
        var decision = rateLimiter.check(RateLimitService.Category.AUTH, ip);
        if (!decision.allowed()) {
            ApiErrorResponses.write(com.hotelapp.core.error.ApiError.tooManyRequestsRetryAfter(
                    "Too many login attempts. Please try again in " + decision.retryAfterSecs()
                            + " seconds.",
                    decision.retryAfterSecs()), httpResponse);
            return null;
        }
        AuthService.LoginResult result = authService.login(request, ip,
                header(httpRequest, "User-Agent"));
        httpResponse.addHeader("Set-Cookie",
                RefreshCookie.build(result.refreshToken(), secureCookies));
        return result.response();
    }

    @PostMapping("/api/auth/refresh")
    public RefreshTokenResponse refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletResponse response) throws IOException {
        if (refreshToken == null || refreshToken.isBlank()) {
            ApiErrorResponses.write(
                    com.hotelapp.core.error.ApiError.unauthorized("Missing refresh token"),
                    response);
            return null;
        }
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        response.addHeader("Set-Cookie", RefreshCookie.build(result.newRefreshToken(),
                secureCookies));
        return result.response();
    }

    @PostMapping("/api/auth/logout")
    public Map<String, Object> logout(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                authService.logout(refreshToken);
            } catch (Exception ignored) {
                // best-effort revoke; the cookie is always cleared
            }
        }
        response.addHeader("Set-Cookie", RefreshCookie.clear(secureCookies));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "Logged out successfully");
        return body;
    }

    @GetMapping("/api/auth/access")
    public AccessSnapshot access() {
        return authService.accessSnapshot(CurrentUser.require().userId());
    }

    private static String clientIp(HttpServletRequest request) {
        String trusted = System.getenv("TRUST_PROXY_HEADERS");
        if (!"true".equalsIgnoreCase(trusted)) {
            return request.getRemoteAddr();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null) {
            return null;
        }
        return value.chars().limit(512).collect(StringBuilder::new,
                StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
