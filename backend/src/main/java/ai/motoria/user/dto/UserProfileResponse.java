package ai.motoria.user.dto;

import ai.motoria.user.domain.IdType;
import ai.motoria.user.domain.IdentityLevel;

import java.util.UUID;

/**
 * The identity number is never returned; only its last 4 characters.
 */
public record UserProfileResponse(
        UUID id,
        String fullName,
        IdType idType,
        String idNumberLast4,
        String phone,
        String email,
        String countryCode,
        IdentityLevel identityLevel) {
}
