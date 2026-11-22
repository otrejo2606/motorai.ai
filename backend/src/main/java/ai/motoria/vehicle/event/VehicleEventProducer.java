package ai.motoria.vehicle.event;

import ai.motoria.common.event.DomainEventPublisher;
import ai.motoria.common.event.EventType;
import ai.motoria.vehicle.domain.Vehicle;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Event meaning (design D1 step 5): requested when the document is received; review.required when the extraction
 * finished and the case goes to the analyst; completed only when the vehicle reaches REGISTRY_VERIFIED.
 * Payloads never carry personal data.
 */
@ApplicationScoped
public class VehicleEventProducer {

    private static final String MODULE = "vehicle";

    @Inject
    DomainEventPublisher eventPublisher;

    public void registryRequested(Vehicle vehicle) {
        eventPublisher.publish(EventType.VEHICLE_REGISTRY_REQUESTED, MODULE, vehicle.getId(),
                new VehicleRegistryRequestedPayload(vehicle.getId(), vehicle.getRegistryDocumentMediaId()));
    }

    public void registryReviewRequired(Vehicle vehicle, UUID inferenceId) {
        eventPublisher.publish(EventType.VEHICLE_REGISTRY_REVIEW_REQUIRED, MODULE, vehicle.getId(),
                new VehicleRegistryReviewRequiredPayload(vehicle.getId(),
                        vehicle.getReviewReason() == null ? null : vehicle.getReviewReason().name(), inferenceId));
    }

    public void registryCompleted(Vehicle vehicle) {
        eventPublisher.publish(EventType.VEHICLE_REGISTRY_COMPLETED, MODULE, vehicle.getId(),
                new VehicleRegistryCompletedPayload(vehicle.getId(), vehicle.getStatus().name(), vehicle.isTransferBlocked()));
    }
}
