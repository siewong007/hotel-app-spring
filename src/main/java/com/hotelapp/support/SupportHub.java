package com.hotelapp.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Port of {@code modules/support/hub.rs::SupportHub} — the in-process fan-out
 * that support writes publish {@code conversation_changed} events to.
 *
 * The WebSocket serving side (GET /api/guest-portal/me/support/socket) is
 * wired in Task 4f; until then subscribers can be registered but none exist.
 */
@Component
public class SupportHub {

    private static final Logger log = LoggerFactory.getLogger(SupportHub.class);

    /** {@code SupportEvent::conversation_changed(guest_id, conversation_id)}. */
    public record ConversationChanged(long guestId, long conversationId) {
    }

    private final List<Consumer<ConversationChanged>> subscribers =
            new CopyOnWriteArrayList<>();

    /** {@code hub.publish(event)} — best-effort fan-out; a dead listener is dropped. */
    public void publish(ConversationChanged event) {
        for (Consumer<ConversationChanged> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                subscribers.remove(subscriber);
                log.debug("Dropped support subscriber: {}", e.getMessage());
            }
        }
    }

    /** Register a listener — used by the WebSocket endpoint in Task 4f. */
    public AutoCloseable subscribe(Consumer<ConversationChanged> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }
}
