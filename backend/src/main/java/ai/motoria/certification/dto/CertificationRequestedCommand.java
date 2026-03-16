package ai.motoria.certification.dto;

import ai.motoria.common.domain.ListingStatus;

import java.util.UUID;

public record CertificationRequestedCommand(
        UUID correlationId,
        String sourceModule,
        UUID listingId,
        ListingStatus listingStatus) {
}
