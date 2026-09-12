package com.hotelapp.communications;

import com.hotelapp.communications.GuestComms.PreferenceUpdateInput;
import com.hotelapp.communications.GuestComms.PreferencesResponse;
import com.hotelapp.portal.PortalAuth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Port of the guest slice of {@code modules/communications/routes.rs} —
 * {@code /guest-portal/me/notification-preferences}. Both routes resolve a
 * plain guest session (no read-budget limiter upstream).
 */
@RestController
public class PreferencesController {

    private final GuestComms comms;
    private final PortalAuth auth;

    public PreferencesController(GuestComms comms, PortalAuth auth) {
        this.comms = comms;
        this.auth = auth;
    }

    @GetMapping("/api/guest-portal/me/notification-preferences")
    public PreferencesResponse getMyPreferences(HttpServletRequest http) {
        long guestId = auth.requireGuestSession(http);
        return comms.getPreferences(guestId);
    }

    @PutMapping("/api/guest-portal/me/notification-preferences")
    public PreferencesResponse updateMyPreferences(HttpServletRequest http,
            @RequestBody PreferenceUpdateInput input) {
        long guestId = auth.requireGuestSession(http);
        return comms.updateMyPreferences(guestId, input,
                auth.clientIp(http), auth.userAgent(http));
    }
}
