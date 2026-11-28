package ai.motoria.vehicle.event;

import java.util.List;
import java.util.UUID;

/**
 * vehicle.registry.anomaly.detected: the registry data does not fit the seller. No plate, owner name or identity
 * number travels in the event.
 *
 * @param sellerId  user who asked for the lookup (the one to notify)
 * @param anomalies RegistryAnomaly codes
 */
public record VehicleRegistryAnomalyPayload(
        UUID vehicleId,
        UUID sellerId,
        List<String> anomalies) {
}
