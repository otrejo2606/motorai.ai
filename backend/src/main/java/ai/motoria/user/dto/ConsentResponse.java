package ai.motoria.user.dto;

import ai.motoria.user.domain.ConsentType;

import java.time.Instant;

/**
 * Current state of one consent type. {@code currentTextVersion} is the version in force (configuration); the other
 * fields describe the user's latest decision and are null/false when the user never decided.
 */
public record ConsentResponse(
        ConsentType type,
        String currentTextVersion,
        String textVersion,
        boolean granted,
        Instant decidedAt) {
}
