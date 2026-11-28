package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.RegistryAnomaly;
import ai.motoria.vehicle.domain.RegistryLookupStatus;

import java.util.List;

/**
 * Result of the registry lookup by plate for the seller.
 *
 * @param registryLookupStatus FOUND or REUSED when registry data is available; otherwise the seller uploads the PDF
 * @param errorCode            stable code when the registry could not answer (VEHICLE_REGISTRY_*), null otherwise
 * @param anomalies            anomalies found against the seller (owner mismatch, pledge); empty when none
 * @param vehicle              the vehicle as it is after the lookup
 */
public record RegistryLookupResponse(
        RegistryLookupStatus registryLookupStatus,
        String errorCode,
        List<RegistryAnomaly> anomalies,
        VehicleResponse vehicle) {
}
