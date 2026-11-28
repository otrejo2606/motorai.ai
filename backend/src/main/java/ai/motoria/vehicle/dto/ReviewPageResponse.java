package ai.motoria.vehicle.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One page of the registry document rendered as an image on the server; imageUrl is a short-lived private URL.
 */
public record ReviewPageResponse(
        int page,
        UUID mediaId,
        String imageUrl,
        Instant expiresAt) {
}
