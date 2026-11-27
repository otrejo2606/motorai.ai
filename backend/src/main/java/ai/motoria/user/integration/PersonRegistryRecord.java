package ai.motoria.user.integration;

import java.time.LocalDate;

/**
 * Minimal answer of the person registry: only what the identity check needs (data minimization).
 *
 * @param personType F (physical person), J (legal person) or E (foreigner), null when not found
 */
public record PersonRegistryRecord(
        boolean found,
        String personType,
        String fullName,
        LocalDate documentExpiresOn,
        String nationality,
        String providerReference) {

    public static PersonRegistryRecord notFound(String providerReference) {
        return new PersonRegistryRecord(false, null, null, null, null, providerReference);
    }
}
