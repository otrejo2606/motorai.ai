package ai.motoria.vehicle.dto;

import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.VehicleStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Seller view of a vehicle (design D9, D9.1). Owner data, VIN and engine number are not included.
 *
 * @param registryRequestFee fee of "que Motoria lo obtenga" from configuration; null while not configured
 * @param ownerMatch         registry owner compared with the caller's verified identity number
 */
public record VehicleResponse(
        UUID id,
        String countryCode,
        String plate,
        VehicleStatus status,
        ReviewReason reviewReason,
        String rejectionReason,
        boolean transferBlocked,
        String transferBlockedReason,
        Instant registryCheckedAt,
        List<VehicleAttributeResponse> attributes,
        List<EncumbranceResponse> encumbrances,
        FeeResponse registryRequestFee,
        boolean registryRequestOpen,
        OwnershipMatch ownerMatch) {
}
