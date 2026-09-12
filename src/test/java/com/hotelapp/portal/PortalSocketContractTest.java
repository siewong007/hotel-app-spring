package com.hotelapp.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hotelapp.guestbooking.FunnelModels.AvailabilityEvent;
import com.hotelapp.support.SupportHub;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contract tests for the guest-portal WebSocket routes mirrored from
 * modules/guest_booking/handlers.rs and modules/support/handlers.rs.
 */
class PortalSocketContractTest {

    private final ObjectMapper json = new ObjectMapper();

    // ---- websocket_token (handlers.rs) -----------------------------------

    @Test
    void websocketTokenComesFromSubprotocolHeader() {
        // upstream test: "hotel-guest-availability, abc123" -> "abc123"
        assertEquals("abc123", PortalSocketHandshake.websocketToken(
                "hotel-guest-availability, abc123", "hotel-guest-availability"));
        assertEquals("abc123", PortalSocketHandshake.websocketToken(
                "hotel-guest-support, abc123", "hotel-guest-support"));
    }

    @Test
    void websocketTokenSkipsEmptyAndMarkerEntries() {
        assertNull(PortalSocketHandshake.websocketToken(
                "hotel-guest-availability", "hotel-guest-availability"));
        assertNull(PortalSocketHandshake.websocketToken(null, "hotel-guest-availability"));
        assertNull(PortalSocketHandshake.websocketToken(
                "hotel-guest-availability,  , ", "hotel-guest-availability"));
        // marker isn't first — still skipped, next non-empty wins
        assertEquals("tok", PortalSocketHandshake.websocketToken(
                "tok, hotel-guest-support", "hotel-guest-support"));
    }

    // ---- event wire shapes ------------------------------------------------

    @Test
    void availabilityEventSerializesSnakeCase() {
        AvailabilityEvent event = new AvailabilityEvent(
                "e1", "availability_changed", "room_inventory_changed",
                7L, LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 22), 3L);
        ObjectNode node = (ObjectNode) json.readTree(json.writeValueAsString(event));
        assertEquals("availability_changed", node.get("event_type").asText());
        assertEquals("room_inventory_changed", node.get("reason").asText());
        assertEquals(7, node.get("room_type_id").asLong());
        assertEquals("2026-09-20", node.get("check_in_date").asText());
        assertEquals(3, node.get("remaining_rooms").asLong());
    }

    @Test
    void supportEventNeverSerializesGuestId() {
        SupportHub.ConversationChanged event = SupportHub.ConversationChanged.of(42L, 9L);
        ObjectNode node = (ObjectNode) json.readTree(json.writeValueAsString(event));
        assertEquals("conversation_changed", node.get("event_type").asText());
        assertEquals(9, node.get("conversation_id").asLong());
        assertTrue(node.get("event_id").isTextual());
        assertFalse(node.has("guest_id"), "guest_id must not cross the wire");
        assertEquals(42L, event.guestId(), "guest_id stays for server-side filtering");
    }
}
