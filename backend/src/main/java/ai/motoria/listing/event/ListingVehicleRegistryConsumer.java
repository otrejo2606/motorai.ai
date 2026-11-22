package ai.motoria.listing.event;

import ai.motoria.common.event.DomainEventEnvelope;
import ai.motoria.common.event.EventType;
import ai.motoria.listing.service.ListingTrustService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.UUID;

@ApplicationScoped
public class ListingVehicleRegistryConsumer {

    private final ObjectMapper objectMapper;
    private final ListingTrustService listingTrustService;

    @Inject
    public ListingVehicleRegistryConsumer(ObjectMapper objectMapper, ListingTrustService listingTrustService) {
        this.objectMapper = objectMapper;
        this.listingTrustService = listingTrustService;
    }

    @Incoming("listing-vehicle-registry-completed-in")
    @Blocking
    public void onRegistryCompleted(String rawEvent) {
        DomainEventEnvelope<CompletedPayload> envelope;
        try {
            envelope = objectMapper.readValue(rawEvent, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize vehicle registry event", exception);
        }
        if (!EventType.VEHICLE_REGISTRY_COMPLETED.routingKey().equals(envelope.eventType())) {
            Log.warnf("Ignoring unexpected listing event type %s", envelope.eventType());
            return;
        }
        CompletedPayload payload = envelope.payload();
        if (payload == null || payload.vehicleId() == null) {
            throw new IllegalArgumentException("Incomplete vehicle.registry.completed payload");
        }
        listingTrustService.markVehicleVerified(payload.vehicleId());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompletedPayload(
            UUID vehicleId,
            String status) {
    }
}
