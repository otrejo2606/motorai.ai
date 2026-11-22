package ai.motoria.notification.dto;

import ai.motoria.common.event.EventType;

import java.util.UUID;

public record VehicleRegistryNotificationCommand(
        EventType eventType,
        UUID correlationId,
        UUID vehicleId,
        String reviewReason,
        String vehicleStatus) {
}
