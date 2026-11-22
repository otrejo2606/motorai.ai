package ai.motoria.listing.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registry block of a listing. Public viewers do not get VIN nor engine number; owner data is never included.
 */
public record ListingVehicleResponse(
        UUID id,
        String countryCode,
        String status,
        boolean transferBlocked,
        String transferBlockedReason,
        Instant registryCheckedAt,
        List<ListingVehicleAttributeResponse> attributes) {
}
