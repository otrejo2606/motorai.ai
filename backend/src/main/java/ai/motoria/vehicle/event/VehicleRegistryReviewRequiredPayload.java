package ai.motoria.vehicle.event;

import java.util.UUID;

public record VehicleRegistryReviewRequiredPayload(
        UUID vehicleId,
        String reason,
        UUID inferenceId) {
}
