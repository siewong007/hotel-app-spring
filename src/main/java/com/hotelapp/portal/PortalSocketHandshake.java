package com.hotelapp.portal;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.error.ApiErrorResponses;
import com.hotelapp.core.security.RateLimitService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.server.HandshakeInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared pre-upgrade gate for the guest-portal WebSocket routes. Mirrors the
 * upstream axum handlers: the portal session token travels inside the
 * {@code Sec-WebSocket-Protocol} header (the browser WebSocket API cannot set
 * arbitrary headers, so the frontend sends it as the second subprotocol
 * entry). The token is the first non-empty protocol that is not the
 * application marker protocol — {@code websocket_token} upstream.
 *
 * <p>On success the resolved guest id is placed in the session attributes as
 * {@value #ATTR_GUEST_ID}. On failure the upgrade is rejected with the same
 * status and JSON envelope an HTTP request would receive.
 */
public class PortalSocketHandshake implements HandshakeInterceptor {

    public static final String ATTR_GUEST_ID = "guestId";

    private static final Logger log = LoggerFactory.getLogger(PortalSocketHandshake.class);

    private final PortalAuth portalAuth;
    private final String markerProtocol;
    private final ObjectMapper json;

    public PortalSocketHandshake(PortalAuth portalAuth, String markerProtocol,
            ObjectMapper json) {
        this.portalAuth = portalAuth;
        this.markerProtocol = markerProtocol;
        this.json = json;
    }

    /** {@code websocket_token}: first non-empty subprotocol other than the marker. */
    public static String websocketToken(String header, String markerProtocol) {
        if (header == null) {
            return null;
        }
        for (String part : header.split(",")) {
            String protocol = part.trim();
            if (!protocol.isEmpty() && !protocol.equals(markerProtocol)) {
                return protocol;
            }
        }
        return null;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            String token = websocketToken(
                    request.getHeaders().getFirst(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL),
                    markerProtocol);
            if (token == null) {
                throw ApiError.unauthorized("Missing guest session token");
            }
            long guestId = portalAuth.requireGuestSessionToken(token);
            RateLimitService.Decision decision = portalAuth.rateLimits().check(
                    RateLimitService.Category.GUEST_PORTAL_TOKEN_READ, "guest:" + guestId);
            if (!decision.allowed()) {
                throw ApiError.tooManyRequestsRetryAfter(
                        "Too many portal requests. Please try again in "
                                + decision.retryAfterSecs() + " seconds.",
                        decision.retryAfterSecs());
            }
            attributes.put(ATTR_GUEST_ID, guestId);
            return true;
        } catch (ApiError error) {
            reject(response, error);
            return false;
        }
    }

    private void reject(ServerHttpResponse response, ApiError error) {
        response.setStatusCode(ApiErrorResponses.statusOf(error));
        if (error.kind() == ApiError.Kind.TOO_MANY_REQUESTS_RETRY_AFTER
                && error.retryAfterSecs() != null) {
            response.getHeaders().add(HttpHeaders.RETRY_AFTER,
                    String.valueOf(error.retryAfterSecs()));
        }
        if (response instanceof ServletServerHttpResponse servlet) {
            HttpServletResponse raw = servlet.getServletResponse();
            raw.setContentType(MediaType.APPLICATION_JSON_VALUE);
            try {
                raw.getWriter().write(json.writeValueAsString(ApiErrorResponses.bodyOf(error)));
            } catch (IOException e) {
                log.debug("Could not write websocket rejection body: {}", e.getMessage());
            }
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // nothing to do — upstream has no post-handshake hook either
    }
}
