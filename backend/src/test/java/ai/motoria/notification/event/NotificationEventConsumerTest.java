package ai.motoria.notification.event;

import ai.motoria.common.event.EventType;
import ai.motoria.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationEventConsumer consumer = new NotificationEventConsumer(objectMapper, notificationService);

    @Test
    void shouldDispatchListingCreatedEvent() {
        UUID correlationId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        consumer.onListingEvent("""
                {
                  "correlationId": "%s",
                  "eventType": "listing.created",
                  "sourceModule": "listing",
                  "timestamp": "2026-03-15T20:00:00Z",
                  "retryCount": 0,
                  "payload": {
                    "listingId": "%s",
                    "sellerId": "%s",
                    "vehicleSpecId": "%s",
                    "status": "DRAFT",
                    "price": 21000
                  }
                }
                """.formatted(correlationId, listingId, sellerId, UUID.randomUUID()));

        verify(notificationService).handleListingEvent(argThat(command ->
                command.eventType() == EventType.LISTING_CREATED
                        && command.correlationId().equals(correlationId)
                        && command.listingId().equals(listingId)
                        && command.sellerId().equals(sellerId)
                        && command.listingStatus().equals("DRAFT")
                        && BigDecimal.valueOf(21000).compareTo(command.price()) == 0));
    }

    @Test
    void shouldDispatchListingPublishedEvent() {
        assertStatusEventDispatched(EventType.LISTING_PUBLISHED, "listing.published", "PUBLISHED");
    }

    @Test
    void shouldDispatchListingSoldEvent() {
        assertStatusEventDispatched(EventType.LISTING_SOLD, "listing.sold", "SOLD");
    }

    @Test
    void shouldDispatchCertificationRequestedEvent() {
        assertStatusEventDispatched(EventType.LISTING_CERTIFICATION_REQUESTED, "listing.certification.requested", "PENDING_REVIEW");
    }

    @Test
    void shouldIgnoreUnsupportedEventType() {
        consumer.onListingEvent("""
                {
                  "correlationId": "%s",
                  "eventType": "listing.updated",
                  "sourceModule": "listing",
                  "timestamp": "2026-03-15T20:00:00Z",
                  "retryCount": 0,
                  "payload": {
                    "listingId": "%s",
                    "status": "DRAFT"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(notificationService);
    }

    @Test
    void shouldRejectMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> consumer.onListingEvent("{invalid-json"));
        verifyNoInteractions(notificationService);
    }

    private void assertStatusEventDispatched(EventType expectedType, String routingKey, String status) {
        UUID correlationId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();

        consumer.onListingEvent("""
                {
                  "correlationId": "%s",
                  "eventType": "%s",
                  "sourceModule": "listing",
                  "timestamp": "2026-03-15T20:00:00Z",
                  "retryCount": 0,
                  "payload": {
                    "listingId": "%s",
                    "status": "%s"
                  }
                }
                """.formatted(correlationId, routingKey, listingId, status));

        verify(notificationService).handleListingEvent(argThat(command ->
                command.eventType() == expectedType
                        && command.correlationId().equals(correlationId)
                        && command.listingId().equals(listingId)
                        && command.sellerId() == null
                        && command.listingStatus().equals(status)
                        && command.price() == null));
    }
}
