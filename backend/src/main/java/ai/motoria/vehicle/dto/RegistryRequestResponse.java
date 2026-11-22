package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.RegistryRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record RegistryRequestResponse(
        UUID id,
        UUID vehicleId,
        String countryCode,
        String plate,
        RegistryRequestStatus status,
        FeeResponse acceptedFee,
        UUID requestedBy,
        UUID documentMediaId,
        Instant fulfilledAt,
        Instant createdAt) {
}
