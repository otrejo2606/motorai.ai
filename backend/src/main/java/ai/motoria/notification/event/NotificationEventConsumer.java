package ai.motoria.notification.event;

import ai.motoria.common.event.DomainEventEnvelope;
import ai.motoria.common.event.EventType;
import ai.motoria.notification.dto.ListingNotificationCommand;
import ai.motoria.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.math.BigDecimal;
import java.util.UUID;

@ApplicationScoped
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Inject
    public NotificationEventConsumer(ObjectMapper objectMapper, NotificationService notificationService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @Incoming("listing-notification-events-in")
    @Blocking
    public void onListingEvent(String rawEvent) {
        DomainEventEnvelope<JsonNode> envelope = readEnvelope(rawEvent);
        EventType eventType = EventType.fromRoutingKey(envelope.eventType())
                .orElse(null);

        if (eventType == null) {
            Log.warnf("Ignoring unsupported notification event type %s", envelope.eventType());
            return;
        }

        ListingNotificationCommand command = switch (eventType) {
            case LISTING_CREATED -> buildCreatedCommand(envelope, eventType);
            case LISTING_PUBLISHED, LISTING_SOLD, LISTING_CERTIFICATION_REQUESTED -> buildStatusCommand(envelope, eventType);
            default -> null;
        };

        if (command == null) {
            Log.warnf("Ignoring event type %s for notification module", eventType.routingKey());
            return;
        }

        notificationService.handleListingEvent(command);
    }

    private DomainEventEnvelope<JsonNode> readEnvelope(String rawEvent) {
        try {
            return objectMapper.readValue(rawEvent, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize notification event", exception);
        }
    }

    private ListingNotificationCommand buildCreatedCommand(DomainEventEnvelope<JsonNode> envelope, EventType eventType) {
        ListingCreatedPayload payload = convertPayload(envelope.payload(), ListingCreatedPayload.class);
        return new ListingNotificationCommand(
                eventType,
                envelope.correlationId(),
                envelope.sourceModule(),
                payload.listingId(),
                payload.sellerId(),
                payload.status(),
                payload.price());
    }

    private ListingNotificationCommand buildStatusCommand(DomainEventEnvelope<JsonNode> envelope, EventType eventType) {
        ListingStatusPayload payload = convertPayload(envelope.payload(), ListingStatusPayload.class);
        return new ListingNotificationCommand(
                eventType,
                envelope.correlationId(),
                envelope.sourceModule(),
                payload.listingId(),
                null,
                payload.status(),
                null);
    }

    private <T> T convertPayload(JsonNode payload, Class<T> payloadType) {
        try {
            return objectMapper.treeToValue(payload, payloadType);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to map event payload", exception);
        }
    }

    private record ListingCreatedPayload(
            UUID listingId,
            UUID sellerId,
            UUID vehicleSpecId,
            String status,
            BigDecimal price) {
    }

    private record ListingStatusPayload(
            UUID listingId,
            String status) {
    }
}
