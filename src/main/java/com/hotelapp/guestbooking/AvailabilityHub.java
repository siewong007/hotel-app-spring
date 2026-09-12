package com.hotelapp.guestbooking;

import com.hotelapp.guestbooking.FunnelModels.AvailabilityEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Port of {@code modules/guest_booking/availability.rs::AvailabilityHub} — the
 * in-process fan-out that booking writes publish availability changes to.
 *
 * The WebSocket serving side (GET /api/guest-portal/me/availability) is wired
 * in Task 4f; until then subscribers can be registered but none exist.
 */
@Component
public class AvailabilityHub {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityHub.class);

    private final List<Consumer<AvailabilityEvent>> subscribers = new CopyOnWriteArrayList<>();

    /** {@code hub.publish(event)} — best-effort fan-out; a dead listener is dropped. */
    public void publish(AvailabilityEvent event) {
        for (Consumer<AvailabilityEvent> subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                subscribers.remove(subscriber);
                log.debug("Dropped availability subscriber: {}", e.getMessage());
            }
        }
    }

    /** Register a listener — used by the WebSocket endpoint in Task 4f. */
    public AutoCloseable subscribe(Consumer<AvailabilityEvent> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }
}
