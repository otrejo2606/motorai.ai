package ai.motoria.vehicle.service;

import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.vehicle.domain.OwnershipPolicy;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import ai.motoria.vehicle.mapper.VehicleMapper;
import ai.motoria.vehicle.repository.VehicleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the shared {@link VehicleRegistryPort} owned by the vehicle module.
 */
@ApplicationScoped
public class VehicleRegistryPortAdapter implements VehicleRegistryPort {

    @Inject
    VehicleRepository vehicleRepository;

    @Inject
    VehicleMapper vehicleMapper;

    @Override
    @Transactional
    public Optional<VehicleSnapshot> findVehicle(UUID vehicleId) {
        return vehicleRepository.findByIdOptional(vehicleId).map(vehicleMapper::toSnapshot);
    }

    @Override
    @Transactional
    public OwnershipMatch checkOwnership(UUID vehicleId, String sellerIdNumberHash) {
        return vehicleRepository.findByIdOptional(vehicleId)
                .map(vehicle -> OwnershipPolicy.compare(
                        vehicle.attributeValue(VehicleAttributeKey.OWNER_ID_HASH).orElse(null), sellerIdNumberHash))
                .orElse(OwnershipMatch.UNKNOWN);
    }
}
