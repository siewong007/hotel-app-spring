package com.hotelapp.auth;

import com.hotelapp.auth.dto.AccessSnapshot;
import com.hotelapp.auth.dto.AuthDtos.EmailVerificationConfirm;
import com.hotelapp.auth.dto.AuthDtos.GoogleLoginRequest;
import com.hotelapp.auth.dto.AuthDtos.LoginLookupRequest;
import com.hotelapp.auth.dto.AuthDtos.LoginLookupResponse;
import com.hotelapp.auth.dto.AuthDtos.RegisterRequest;
import com.hotelapp.auth.dto.AuthDtos.ResendVerificationRequest;
import com.hotelapp.auth.dto.AuthResponse;
import com.hotelapp.auth.dto.LoginRequest;
import com.hotelapp.auth.dto.RefreshTokenResponse;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.error.ApiErrorResponses;
import com.hotelapp.core.web.ClientIp;
import com.hotelapp.core.web.ClientTimezone;
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
    private final Turnstile turnstile;
    private final RateLimitService rateLimiter;
    private final ClientIp clientIp;
    private final boolean secureCookies;

    public AuthController(AuthService authService, Turnstile turnstile,
            RateLimitService rateLimiter, ClientIp clientIp,
            @Value("${app.environment:development}") String environment) {
        this.authService = authService;
        this.turnstile = turnstile;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.secureCookies = "production".equalsIgnoreCase(environment);
    }

    /**
     * Upstream `login_lookup`: rate-limited but no Turnstile — the response is
     * a constant `exists: true` for any non-empty identifier, so there is
     * nothing for a bot to harvest.
     */
    @PostMapping(value = "/api/auth/login/lookup",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginLookupResponse loginLookup(@Valid @RequestBody LoginLookupRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String ip = clientIp.extract(httpRequest);
        if (rateLimited(RateLimitService.Category.AUTH, ip,
                "Too many login attempts. Please try again in ", httpResponse)) {
            return null;
        }
        return authService.lookupLoginIdentifier(request);
    }

    @PostMapping(value = "/api/auth/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String ip = clientIp.extract(httpRequest);
        if (rateLimited(RateLimitService.Category.AUTH, ip,
                "Too many login attempts. Please try again in ", httpResponse)) {
            return null;
        }
        // After the rate limiter (cheap, local) and before any password work,
        // so a bot never reaches the hashing path. Tokens are single-use, so
        // the client mints a fresh one per attempt — including the second
        // /auth/login call that carries the 2FA code.
        turnstile.verifyRequest(httpRequest, ip, "login");
        AuthService.LoginResult result = authService.login(request, ip,
                header(httpRequest, "User-Agent"),
                ClientTimezone.extract(httpRequest));
        httpResponse.addHeader("Set-Cookie",
                RefreshCookie.build(result.refreshToken(), secureCookies));
        return result.response();
    }

    @PostMapping(value = "/api/auth/google", produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String ip = clientIp.extract(httpRequest);
        if (rateLimited(RateLimitService.Category.AUTH, ip,
                "Too many Google sign-in attempts. Please try again in ", httpResponse)) {
            return null;
        }
        AuthService.LoginResult result = authService.loginWithGoogle(request, ip,
                header(httpRequest, "User-Agent"), ClientTimezone.extract(httpRequest));
        httpResponse.addHeader("Set-Cookie",
                RefreshCookie.build(result.refreshToken(), secureCookies));
        return result.response();
    }

    @PostMapping(value = "/api/auth/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String ip = clientIp.extract(httpRequest);
        if (rateLimited(RateLimitService.Category.REGISTER, ip,
                "Too many registration attempts. Please try again in ", httpResponse)) {
            return null;
        }
        turnstile.verifyRequest(httpRequest, ip, "register");
        return authService.register(request, ip, header(httpRequest, "User-Agent"));
    }

    @PostMapping(value = "/api/auth/verify-email",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> verifyEmail(@Valid @RequestBody EmailVerificationConfirm request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String ip = clientIp.extract(httpRequest);
        if (rateLimited(RateLimitService.Category.SENSITIVE, ip,
                "Too many requests. Try again in ", httpResponse)) {
            return null;
        }
        return authService.verifyEmail(request.token());
    }

    @PostMapping(value = "/api/auth/resend-verification",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        String ip = clientIp.extract(httpRequest);
        if (rateLimited(RateLimitService.Category.SENSITIVE, ip,
                "Too many requests. Try again in ", httpResponse)) {
            return null;
        }
        return authService.resendVerification(request.email());
    }

    @PostMapping("/api/auth/refresh")
    public RefreshTokenResponse refresh(
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest, HttpServletResponse response) throws IOException {
        String ip = clientIp.extract(httpRequest);
        if (rateLimited(RateLimitService.Category.SENSITIVE, ip,
                "Too many refresh attempts. Please try again in ", response)) {
            return null;
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            ApiErrorResponses.write(ApiError.unauthorized("Missing refresh token"), response);
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

    private boolean rateLimited(RateLimitService.Category category, String ip,
            String messagePrefix, HttpServletResponse response) throws IOException {
        var decision = rateLimiter.check(category, ip);
        if (decision.allowed()) {
            return false;
        }
        ApiErrorResponses.write(ApiError.tooManyRequestsRetryAfter(
                messagePrefix + decision.retryAfterSecs() + " seconds.",
                decision.retryAfterSecs()), response);
        return true;
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
