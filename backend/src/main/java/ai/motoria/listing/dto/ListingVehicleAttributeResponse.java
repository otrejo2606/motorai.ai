package ai.motoria.listing.dto;

public record ListingVehicleAttributeResponse(
        String key,
        String value,
        String source,
        boolean verified) {
}
