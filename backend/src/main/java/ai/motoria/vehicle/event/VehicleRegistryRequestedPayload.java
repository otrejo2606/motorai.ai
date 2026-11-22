package ai.motoria.vehicle.event;

import java.util.UUID;

public record VehicleRegistryRequestedPayload(
        UUID vehicleId,
        UUID documentMediaId) {
}
