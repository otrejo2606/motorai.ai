package ai.motoria.listing.dto;

import java.util.UUID;

public record ListingPhotoResponse(
        String slot,
        UUID mediaId,
        String publicUrl) {
}
