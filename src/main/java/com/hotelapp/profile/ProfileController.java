package com.hotelapp.profile;

import com.hotelapp.auth.dto.AuthDtos.CompleteGuestProfileRequest;
import com.hotelapp.auth.dto.AuthDtos.PasswordUpdateInput;
import com.hotelapp.auth.dto.AuthDtos.UserProfile;
import com.hotelapp.auth.dto.AuthDtos.UserProfileUpdate;
import com.hotelapp.auth.dto.AuthDtos.UserSessionInfo;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.error.ApiErrorResponses;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.RateLimitService;
import com.hotelapp.core.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the profile slice of {@code routes/profile.rs}: get/patch/complete,
 * password rotation, and session list/revoke. Password update and session
 * revocation are sensitive-limited; the reads are auth-only.
 */
@RestController
public class ProfileController {

    private final ProfileService profile;
    private final RateLimitService rateLimiter;
    private final ClientIp clientIp;

    public ProfileController(ProfileService profile, RateLimitService rateLimiter,
            ClientIp clientIp) {
        this.profile = profile;
        this.rateLimiter = rateLimiter;
        this.clientIp = clientIp;
    }

    @GetMapping(value = "/api/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserProfile getProfile() {
        return profile.getUserProfile(CurrentUser.require().userId());
    }

    @PatchMapping(value = "/api/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserProfile updateProfile(@Valid @RequestBody UserProfileUpdate req) {
        return profile.updateUserProfile(CurrentUser.require().userId(), req);
    }

    @PostMapping(value = "/api/profile/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserProfile completeProfile(@Valid @RequestBody CompleteGuestProfileRequest req) {
        return profile.completeGuestProfile(CurrentUser.require().userId(), req);
    }

    @PostMapping(value = "/api/profile/password", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> updatePassword(@Valid @RequestBody PasswordUpdateInput req,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        profile.updatePassword(CurrentUser.require().userId(), req);
        return Map.of("message", "Password updated successfully");
    }

    @GetMapping(value = "/api/profile/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UserSessionInfo> listSessions() {
        var user = CurrentUser.require();
        return profile.listSessions(user.userId(), user.claims().sid());
    }

    @DeleteMapping(value = "/api/profile/sessions/{sessionId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> revokeSession(@PathVariable String sessionId,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse)
            throws IOException {
        if (limited(httpRequest, httpResponse)) {
            return null;
        }
        profile.revokeSession(CurrentUser.require().userId(), sessionId);
        return Map.of("message", "Session logged out successfully");
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
