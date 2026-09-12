package com.hotelapp.auth;

import com.hotelapp.auth.dto.AuthDtos.PasskeyInfo;
import com.hotelapp.auth.dto.AuthDtos.PasskeyLoginFinish;
import com.hotelapp.auth.dto.AuthDtos.PasskeyLoginStart;
import com.hotelapp.auth.dto.AuthDtos.PasskeyRegistrationFinish;
import com.hotelapp.auth.dto.AuthDtos.PasskeyRegistrationStart;
import com.hotelapp.auth.dto.AuthDtos.PasskeyUpdateInput;
import com.hotelapp.auth.dto.AuthResponse;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.error.ApiErrorResponses;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.core.web.ClientIp;
import com.hotelapp.core.web.ClientTimezone;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of {@code routes/passkey.rs} plus the {@code /profile/passkeys*}
 * management routes from {@code routes/profile.rs}. Registration routes are
 * sensitive-limited and authenticated; login routes are auth-limited and
 * public.
 */
@RestController
public class PasskeyController {

    private final PasskeyService passkeys;
    private final RateLimitService rateLimiter;
    private final ClientIp clientIp;
    private final boolean secureCookies;

    public PasskeyController(PasskeyService passkeys, RateLimitService rateLimiter,
            ClientIp clientIp,
            @Value("${app.environment:development}") String environment) {
        this.passkeys = passkeys;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
        this.secureCookies = "production".equalsIgnoreCase(environment);
    }

    // ---- registration (authenticated) -----------------------------------------

    @PostMapping(value = "/api/auth/passkey/register/start",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> registerStart(@Valid @RequestBody PasskeyRegistrationStart req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        return passkeys.registerStart(CurrentUser.require().userId(), req);
    }

    @PostMapping(value = "/api/auth/passkey/register/finish",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> registerFinish(@Valid @RequestBody PasskeyRegistrationFinish req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        passkeys.registerFinish(CurrentUser.require().userId(), req);
        return Map.of("message", "Passkey registered successfully");
    }

    // ---- login (public) ---------------------------------------------------------

    @PostMapping(value = "/api/auth/passkey/login/start",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> loginStart(@Valid @RequestBody PasskeyLoginStart req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (authLimited(httpRequest, httpResponse)) {
            return null;
        }
        return passkeys.loginStart(req);
    }

    @PostMapping(value = "/api/auth/passkey/login/finish",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse loginFinish(@Valid @RequestBody PasskeyLoginFinish req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (authLimited(httpRequest, httpResponse)) {
            return null;
        }
        String ip = clientIp.extract(httpRequest);
        AuthService.LoginResult result = passkeys.loginFinish(req, ip,
                userAgent(httpRequest), ClientTimezone.extract(httpRequest));
        httpResponse.addHeader("Set-Cookie",
                RefreshCookie.build(result.refreshToken(), secureCookies));
        return result.response();
    }

    // ---- profile management ----------------------------------------------------

    @GetMapping(value = "/api/profile/passkeys", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PasskeyInfo> listPasskeys() {
        return passkeys.listPasskeys(CurrentUser.require().userId());
    }

    @PatchMapping(value = "/api/profile/passkeys/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updatePasskey(@PathVariable String id,
            @Valid @RequestBody PasskeyUpdateInput req, HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        passkeys.updatePasskey(CurrentUser.require().userId(), id, req);
        return Map.of("message", "Passkey updated successfully");
    }

    @DeleteMapping(value = "/api/profile/passkeys/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> deletePasskey(@PathVariable String id,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        passkeys.deletePasskey(CurrentUser.require().userId(), id);
        return Map.of("message", "Passkey deleted successfully");
    }

    // ---- helpers -----------------------------------------------------------------

    private boolean limited(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        var decision = rateLimiter.check(RateLimitService.Category.SENSITIVE,
                clientIp.extract(request));
        if (decision.allowed()) {
            return false;
        }
        ApiErrorResponses.write(ApiError.tooManyRequestsRetryAfter(
                "Too many requests. Try again in " + decision.retryAfterSecs() + " seconds.",
                decision.retryAfterSecs()), response);
        return true;
    }

    private boolean authLimited(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        var decision = rateLimiter.check(RateLimitService.Category.AUTH,
                clientIp.extract(request));
        if (decision.allowed()) {
            return false;
        }
        ApiErrorResponses.write(ApiError.tooManyRequestsRetryAfter(
                "Too many passkey attempts. Please try again in " + decision.retryAfterSecs()
                        + " seconds.",
                decision.retryAfterSecs()), response);
        return true;
    }

    private static String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (value == null) {
            return null;
        }
        return value.chars().limit(512).collect(StringBuilder::new,
                StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

}
