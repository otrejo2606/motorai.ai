package ai.motoria.user.dto;

import ai.motoria.user.domain.IdType;
import ai.motoria.user.domain.IdentityVerificationStatus;
import ai.motoria.user.domain.RegistryMatchStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Analyst view: the media ids are opened with GET /media/{id}/read-url (BACKOFFICE, audited).
 * The registry fields show what the person registry (Credid) says about the document; null until it was checked.
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
        String rejectionReason,
        RegistryMatchStatus registryStatus,
        String registryProvider,
        String registryPersonType,
        String registryFullName,
        LocalDate registryDocumentExpiresOn,
        Instant registryCheckedAt) {
}
