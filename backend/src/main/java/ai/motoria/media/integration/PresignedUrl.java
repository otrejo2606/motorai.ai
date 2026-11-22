package ai.motoria.media.integration;

import java.time.Instant;

public record PresignedUrl(
        String url,
        Instant expiresAt) {
}
