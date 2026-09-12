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

    /**
     * {@code SupportEvent::conversation_changed(guest_id, conversation_id)}.
     * Serialized as {@code {event_id, event_type, conversation_id}} — guest_id
     * never crosses the wire (upstream marks it {@code #[serde(skip)]}).
     */
    public record ConversationChanged(
            @com.fasterxml.jackson.annotation.JsonProperty("event_id") String eventId,
            @com.fasterxml.jackson.annotation.JsonProperty("event_type") String eventType,
            @com.fasterxml.jackson.annotation.JsonIgnore long guestId,
            @com.fasterxml.jackson.annotation.JsonProperty("conversation_id")
            long conversationId) {

        public static ConversationChanged of(long guestId, long conversationId) {
            return new ConversationChanged(java.util.UUID.randomUUID().toString(),
                    "conversation_changed", guestId, conversationId);
        }
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
