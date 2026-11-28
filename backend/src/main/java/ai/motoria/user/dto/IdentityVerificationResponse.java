package ai.motoria.user.dto;

import ai.motoria.user.domain.IdentityVerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record IdentityVerificationResponse(
        UUID id,
        IdentityVerificationStatus status,
        String challengeCode,
        Instant expiresAt,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason) {
}
