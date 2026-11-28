package ai.motoria.vehicle.service;

import ai.motoria.vehicle.domain.RegistryAnomaly;
import ai.motoria.vehicle.domain.RegistryLookupStatus;

import java.util.List;

/**
 * Outcome of a registry lookup by plate, without the vehicle view (built by the caller when it needs it).
 *
 * @param errorCode VEHICLE_REGISTRY_* when the registry could not answer, null otherwise
 */
public record RegistryLookupResult(
        RegistryLookupStatus status,
        String errorCode,
        List<RegistryAnomaly> anomalies) {

    public RegistryLookupResult {
        anomalies = anomalies == null ? List.of() : List.copyOf(anomalies);
    }

    static RegistryLookupResult of(RegistryLookupStatus status) {
        return new RegistryLookupResult(status, null, List.of());
    }

    static RegistryLookupResult failed(RegistryLookupStatus status, String errorCode) {
        return new RegistryLookupResult(status, errorCode, List.of());
    }
}
