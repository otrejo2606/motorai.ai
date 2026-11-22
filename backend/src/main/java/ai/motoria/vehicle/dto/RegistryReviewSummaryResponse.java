package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.VehicleStatus;

import java.time.Instant;
import java.util.UUID;

public record RegistryReviewSummaryResponse(
        UUID vehicleId,
        String countryCode,
        String plate,
        VehicleStatus status,
        ReviewReason reviewReason,
        UUID documentMediaId,
        Instant updatedAt) {
}
