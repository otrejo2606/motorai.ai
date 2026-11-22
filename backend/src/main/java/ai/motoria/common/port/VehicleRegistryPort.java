package ai.motoria.common.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by {@code vehicle}. Lets {@code listing} prefill the listing from the verified registry
 * data and check the seller's ownership without reading the vehicle schema.
 */
public interface VehicleRegistryPort {

    Optional<VehicleSnapshot> findVehicle(UUID vehicleId);

    OwnershipMatch checkOwnership(UUID vehicleId, String sellerIdNumberHash);
}
