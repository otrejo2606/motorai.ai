package ai.motoria.vehicle.service;

import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.repository.VehicleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Creates a vehicle in its own transaction so a concurrent duplicate (vehicle_country_plate_key) fails here
 * without marking the caller's transaction for rollback; the caller then re-reads the existing vehicle.
 */
@ApplicationScoped
public class VehicleCreator {

    @Inject
    VehicleRepository vehicleRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public UUID create(String countryCode, String plate, UUID requestedBy) {
        Vehicle vehicle = Vehicle.register(countryCode, plate, requestedBy);
        vehicleRepository.persistAndFlush(vehicle);
        return vehicle.getId();
    }
}
