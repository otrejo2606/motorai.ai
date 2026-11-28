package ai.motoria.media.dto;

import java.time.Instant;

public record ReadUrlResponse(
        String url,
        Instant expiresAt) {
}
