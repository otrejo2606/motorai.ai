package ai.motoria.user.dto;

import java.time.Instant;
import java.util.UUID;

public record IdentityChallengeResponse(
        UUID id,
        String challengeCode,
        Instant expiresAt) {
}
