package com.hotelapp.support;

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
 * {@code serve_socket} for {@code GET /api/guest-portal/me/support/socket}:
 * subscribes to the {@link SupportHub} and forwards only events belonging to
 * the authenticated guest (upstream filters on {@code event.guest_id}, which
 * is never serialized). Ping frames are answered by the container;
 * close/error end the session.
 */
@Component
public class SupportSocketHandler extends TextWebSocketHandler
        implements SubProtocolCapable {

    public static final String PROTOCOL = "hotel-guest-support";

    private static final String ATTR_SUBSCRIPTION = "supportSubscription";
    private static final Logger log = LoggerFactory.getLogger(SupportSocketHandler.class);

    private final SupportHub hub;
    private final ObjectMapper json;

    public SupportSocketHandler(SupportHub hub, ObjectMapper json) {
        this.hub = hub;
        this.json = json;
    }

    /** Upstream {@code .protocols(["hotel-guest-support"])} — echoes the marker. */
    @Override
    public List<String> getSubProtocols() {
        return List.of(PROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object guestId = session.getAttributes().get(PortalSocketHandshake.ATTR_GUEST_ID);
        if (!(guestId instanceof Long id)) {
            closeQuietly(session, null);
            return;
        }
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(
                session, 10_000, 64 * 1024);
        AutoCloseable subscription = hub.subscribe(event -> {
            if (event.guestId() != id) {
                return;
            }
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
                log.debug("Support subscription cleanup failed: {}", e.getMessage());
            }
        }
    }

    private void closeQuietly(WebSocketSession session, Throwable cause) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            log.debug("Support socket already closed: {}", e.getMessage());
        }
    }
}
