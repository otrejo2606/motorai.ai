package ai.motoria.vehicle.domain;

/**
 * Result of one call to the vehicle registry. FAILED rows carry the error code; only FOUND and NOT_FOUND count for the
 * daily quota (they are the answers the provider bills).
 */
public enum RegistryLookupOutcome {
    FOUND,
    NOT_FOUND,
    FAILED
}
