package com.hotelapp.portal;

import com.hotelapp.guestbooking.AvailabilitySocketHandler;
import com.hotelapp.support.SupportSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the guest-portal WebSocket routes — raw sockets, not STOMP,
 * matching the upstream axum upgrades and the frontend's subprotocol-based
 * token transport:
 *
 * <ul>
 *   <li>{@code GET /api/guest-portal/me/availability} → availability hub fan-out
 *   <li>{@code GET /api/guest-portal/me/support/socket} → per-guest support events
 * </ul>
 *
 * The parity checker maps each {@code addHandler(..., "path")} call as a GET.
 */
@Configuration
@EnableWebSocket
public class PortalWebSocketConfig implements WebSocketConfigurer {

    private final AvailabilitySocketHandler availabilitySocket;
    private final SupportSocketHandler supportSocket;
    private final PortalAuth portalAuth;
    private final ObjectMapper json;

    public PortalWebSocketConfig(AvailabilitySocketHandler availabilitySocket,
            SupportSocketHandler supportSocket, PortalAuth portalAuth, ObjectMapper json) {
        this.availabilitySocket = availabilitySocket;
        this.supportSocket = supportSocket;
        this.portalAuth = portalAuth;
        this.json = json;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(availabilitySocket, "/api/guest-portal/me/availability")
                .addInterceptors(new PortalSocketHandshake(
                        portalAuth, AvailabilitySocketHandler.PROTOCOL, json))
                // Upstream performs no Origin check on the upgrade.
                .setAllowedOriginPatterns("*");
        registry.addHandler(supportSocket, "/api/guest-portal/me/support/socket")
                .addInterceptors(new PortalSocketHandshake(
                        portalAuth, SupportSocketHandler.PROTOCOL, json))
                .setAllowedOriginPatterns("*");
    }
}
