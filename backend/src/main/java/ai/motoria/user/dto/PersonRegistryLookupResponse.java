package ai.motoria.user.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Minimal answer of a backoffice lookup.
 *
 * @param personType F (physical person), J (legal person) or E (foreigner)
 * @param fromCache  true when a fresh check was reused and nothing was paid
 */
public record PersonRegistryLookupResponse(
        boolean found,
        String provider,
        String personType,
        String fullName,
        LocalDate documentExpiresOn,
        boolean documentExpired,
        String nationality,
        Instant checkedAt,
        boolean fromCache) {
}
