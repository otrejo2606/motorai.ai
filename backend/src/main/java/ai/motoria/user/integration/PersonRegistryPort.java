package ai.motoria.user.integration;

import ai.motoria.user.domain.IdType;

/**
 * Person registry queried by identity document (Credid in Costa Rica), interchangeable by configuration
 * (motoria.user.person-registry.provider = stub | credid). It only answers who the document belongs to; it does not
 * replace the selfie review (it has no liveness check).
 */
public interface PersonRegistryPort {

    String providerName();

    /**
     * @param idType   NATIONAL_ID or RESIDENT_ID (passports are not in the registry)
     * @param idNumber normalized number: upper case letters and digits only
     * @throws PersonRegistryUnavailableException when the registry cannot answer
     */
    PersonRegistryRecord lookup(IdType idType, String idNumber);
}
