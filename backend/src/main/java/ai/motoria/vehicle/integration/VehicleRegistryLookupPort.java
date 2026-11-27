package ai.motoria.vehicle.integration;

/**
 * Vehicle registry queried by plate (Credid in Costa Rica), interchangeable by configuration
 * (motoria.vehicle.registry.provider = stub | credid). First option of the seller flow; the registry PDF from
 * rnpdigital stays as the fallback when it cannot answer.
 */
public interface VehicleRegistryLookupPort {

    String providerName();

    /**
     * @param normalizedPlate Costa Rica plate, upper case letters and digits only
     * @throws VehicleRegistryUnavailableException when the registry cannot answer
     */
    VehicleRegistryRecord lookup(String normalizedPlate);
}
