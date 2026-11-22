package ai.motoria.vehicle.event;

import ai.motoria.common.event.DomainEventEnvelope;
import ai.motoria.common.event.EventType;
import ai.motoria.vehicle.service.RegistryProcessingService;
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

/**
 * Runs the registry extraction in the background when a registry document is received.
 */
@ApplicationScoped
public class VehicleRegistryEventConsumer {

    private final ObjectMapper objectMapper;
    private final RegistryProcessingService registryProcessingService;

    @Inject
    public VehicleRegistryEventConsumer(ObjectMapper objectMapper, RegistryProcessingService registryProcessingService) {
        this.objectMapper = objectMapper;
        this.registryProcessingService = registryProcessingService;
    }

    @Incoming("vehicle-registry-requested-in")
    @Blocking
    public void onRegistryRequested(String rawEvent) {
        DomainEventEnvelope<RequestedPayload> envelope = readEnvelope(rawEvent);
        if (!EventType.VEHICLE_REGISTRY_REQUESTED.routingKey().equals(envelope.eventType())) {
            Log.warnf("Ignoring unexpected vehicle event type %s", envelope.eventType());
            return;
        }
        RequestedPayload payload = envelope.payload();
        if (payload == null || payload.vehicleId() == null || payload.documentMediaId() == null) {
            throw new IllegalArgumentException("Incomplete vehicle.registry.requested payload");
        }
        registryProcessingService.process(payload.vehicleId(), payload.documentMediaId(), envelope.correlationId());
    }

    private DomainEventEnvelope<RequestedPayload> readEnvelope(String rawEvent) {
        try {
            return objectMapper.readValue(rawEvent, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to deserialize vehicle registry event", exception);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RequestedPayload(
            UUID vehicleId,
            UUID documentMediaId) {
    }
}
