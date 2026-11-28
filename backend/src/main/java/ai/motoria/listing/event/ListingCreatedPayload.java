package ai.motoria.listing.event;

import ai.motoria.common.domain.ListingStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ListingCreatedPayload(
        UUID listingId,
        UUID sellerId,
        UUID vehicleId,
        ListingStatus status,
        BigDecimal price) {
}
