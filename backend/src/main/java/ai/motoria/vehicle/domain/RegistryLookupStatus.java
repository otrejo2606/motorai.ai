package ai.motoria.vehicle.domain;

/**
 * What the seller gets back from POST /vehicles/{id}/registry-lookup. FOUND and REUSED carry registry data; any other
 * value leaves the vehicle as it was and the seller uploads the registry PDF (fallback).
 */
public enum RegistryLookupStatus {
    FOUND,
    REUSED,
    NOT_FOUND,
    UNAVAILABLE,
    NOT_CONFIGURED,
    QUOTA_EXHAUSTED;

    public boolean hasRegistryData() {
        return this == FOUND || this == REUSED;
    }
}
