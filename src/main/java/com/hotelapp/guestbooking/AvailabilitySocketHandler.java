package com.hotelapp.guestbooking;

import com.hotelapp.portal.PortalSocketHandshake;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code serve_socket} for {@code GET /api/guest-portal/me/availability}:
 * subscribes the socket to the {@link AvailabilityHub} and forwards every
 * event as a JSON text frame. Ping frames are answered by the container;
 * close/error end the session, matching upstream's select loop.
 */
@Component
public class AvailabilitySocketHandler extends TextWebSocketHandler
        implements SubProtocolCapable {

    public static final String PROTOCOL = "hotel-guest-availability";

    private static final String ATTR_SUBSCRIPTION = "availabilitySubscription";
    private static final Logger log = LoggerFactory.getLogger(AvailabilitySocketHandler.class);

    private final AvailabilityHub hub;
    private final ObjectMapper json;

    public AvailabilitySocketHandler(AvailabilityHub hub, ObjectMapper json) {
        this.hub = hub;
        this.json = json;
    }

    /** Upstream {@code .protocols(["hotel-guest-availability"])} — echoes the marker. */
    @Override
    public List<String> getSubProtocols() {
        return List.of(PROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, 10_000, 64 * 1024);
        AutoCloseable subscription = hub.subscribe(event -> {
            try {
                safe.sendMessage(new TextMessage(json.writeValueAsString(event)));
            } catch (IOException e) {
                closeQuietly(session, e);
            }
        });
        session.getAttributes().put(ATTR_SUBSCRIPTION, subscription);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unsubscribe(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        unsubscribe(session);
        closeQuietly(session, exception);
    }

    private void unsubscribe(WebSocketSession session) {
        Object subscription = session.getAttributes().remove(ATTR_SUBSCRIPTION);
        if (subscription instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Availability subscription cleanup failed: {}", e.getMessage());
            }
        }
    }

    private void closeQuietly(WebSocketSession session, Throwable cause) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            log.debug("Availability socket already closed: {}", e.getMessage());
        }
    }
}
