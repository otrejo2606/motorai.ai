package ai.motoria.common.port;

import java.time.Instant;
import java.util.UUID;

public record MediaReadUrl(
        UUID mediaId,
        String url,
        Instant expiresAt) {
}
