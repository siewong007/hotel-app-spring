package com.hotelapp.auth;

import com.hotelapp.auth.dto.AuthDtos.RegenerateBackupCodesRequest;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorDisableRequest;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorEnableRequest;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorStatusResponse;
import com.hotelapp.auth.dto.AuthDtos.TwoFactorVerifyRequest;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.error.ApiErrorResponses;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.core.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of {@code routes/two_factor.rs} plus the {@code /profile/2fa/*} aliases
 * mounted by {@code routes/profile.rs} — every route is sensitive-limited and
 * authenticated.
 */
@RestController
public class TwoFactorController {

    private final TwoFactorService twoFactor;
    private final RateLimitService rateLimiter;
    private final ClientIp clientIp;

    public TwoFactorController(TwoFactorService twoFactor, RateLimitService rateLimiter,
            ClientIp clientIp) {
        this.twoFactor = twoFactor;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
    }

    @PostMapping(value = {"/api/auth/2fa/setup", "/api/profile/2fa/setup"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> setup(HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        return twoFactor.setup(CurrentUser.require().userId());
    }

    @PostMapping(value = {"/api/auth/2fa/enable", "/api/profile/2fa/enable"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> enable(@Valid @RequestBody TwoFactorEnableRequest req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        return twoFactor.enable(CurrentUser.require().userId(), req);
    }

    @PostMapping(value = {"/api/auth/2fa/disable", "/api/profile/2fa/disable"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> disable(@Valid @RequestBody TwoFactorDisableRequest req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        return twoFactor.disable(CurrentUser.require().userId(), req);
    }

    @GetMapping(value = {"/api/auth/2fa/status", "/api/profile/2fa/status"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public TwoFactorStatusResponse status(HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        return twoFactor.status(CurrentUser.require().userId());
    }

    @PostMapping(value = {"/api/auth/2fa/verify", "/api/profile/2fa/verify"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> verify(@Valid @RequestBody TwoFactorVerifyRequest req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        return twoFactor.verify(CurrentUser.require().userId(), req);
    }

    @PostMapping(value = "/api/auth/2fa/regenerate-backup-codes",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> regenerateBackupCodes(
            @Valid @RequestBody RegenerateBackupCodesRequest req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        return twoFactor.regenerateBackupCodes(CurrentUser.require().userId(), req);
    }

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
}
