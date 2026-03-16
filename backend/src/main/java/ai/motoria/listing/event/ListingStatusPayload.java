package ai.motoria.listing.event;

import ai.motoria.common.domain.ListingStatus;

import java.util.UUID;

public record ListingStatusPayload(
        UUID listingId,
        ListingStatus status) {
}
