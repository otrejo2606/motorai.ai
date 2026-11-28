package ai.motoria.user.integration;

import java.time.Instant;

public record IdentityChallenge(
        String code,
        Instant expiresAt) {
}
