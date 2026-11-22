package ai.motoria.vehicle.event;

import java.util.UUID;

public record VehicleRegistryCompletedPayload(
        UUID vehicleId,
        String status,
        boolean transferBlocked) {
}
