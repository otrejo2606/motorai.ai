package ai.motoria.notification.dto;

import ai.motoria.common.event.EventType;

import java.util.List;
import java.util.UUID;

/**
 * Tell the seller that the registry data does not fit them (owner mismatch, pledge) or that a photo reads something
 * that does not fit the vehicle (VIN_MISMATCH, ODOMETER_MISMATCH, add-captura-guiada-fotos).
 *
 * @param anomalies RegistryAnomaly or PhotoAnomaly codes (the channel translates them)
 */
public record VehicleRegistryAnomalyNotificationCommand(
        EventType eventType,
        UUID correlationId,
        UUID vehicleId,
        UUID sellerId,
        List<String> anomalies) {
}
