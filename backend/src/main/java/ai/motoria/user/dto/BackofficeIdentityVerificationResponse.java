package ai.motoria.user.dto;

import ai.motoria.user.domain.IdType;
import ai.motoria.user.domain.IdentityVerificationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Analyst view: the media ids are opened with GET /media/{id}/read-url (BACKOFFICE, audited).
 */
public record BackofficeIdentityVerificationResponse(
        UUID id,
        UUID userId,
        String fullName,
        IdType idType,
        String idNumberLast4,
        IdentityVerificationStatus status,
        String challengeCode,
        UUID documentFrontMediaId,
        UUID documentBackMediaId,
        UUID selfieMediaId,
        Instant submittedAt,
        String reviewedBy,
        Instant reviewedAt,
        String rejectionReason) {
}
