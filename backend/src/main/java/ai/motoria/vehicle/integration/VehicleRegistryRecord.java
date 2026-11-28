package ai.motoria.vehicle.integration;

import ai.motoria.vehicle.domain.VehicleAttributeKey;

import java.util.Map;

/**
 * Minimal answer of the vehicle registry by plate. The owner identity number only lives in memory: the service turns
 * it into OWNER_ID_HASH before anything is stored, and {@link #toString()} never prints it.
 *
 * @param attributes    registry values as received (owner data excluded), keyed by attribute
 * @param pledged       the registry reports a pledge (PoseeGarantias or TotalPrenda greater than zero)
 * @param pledgeAmount  TotalPrenda as received, null when absent
 * @param ownerName     first registered owner, null when absent
 * @param ownerIdNumber identity number of the first registered owner, null when absent
 */
public record VehicleRegistryRecord(
        boolean found,
        Map<VehicleAttributeKey, String> attributes,
        boolean pledged,
        String pledgeAmount,
        String ownerName,
        String ownerIdNumber,
        String providerReference) {

    public VehicleRegistryRecord {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static VehicleRegistryRecord notFound(String providerReference) {
        return new VehicleRegistryRecord(false, Map.of(), false, null, null, null, providerReference);
    }

    @Override
    public String toString() {
        return "VehicleRegistryRecord[found=" + found + ", attributes=" + attributes.keySet() + ", pledged=" + pledged
                + ", providerReference=" + providerReference + "]";
    }
}
