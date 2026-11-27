package ai.motoria.notification.dto;

import ai.motoria.common.event.EventType;

import java.util.List;
import java.util.UUID;

/**
 * Tell the seller that the registry data does not fit them (owner mismatch, pledge).
 *
 * @param anomalies RegistryAnomaly codes (the channel translates them)
 */
public record VehicleRegistryAnomalyNotificationCommand(
        EventType eventType,
        UUID correlationId,
        UUID vehicleId,
        UUID sellerId,
        List<String> anomalies) {
}
