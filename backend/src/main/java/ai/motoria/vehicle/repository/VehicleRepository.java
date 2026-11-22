package ai.motoria.vehicle.repository;

import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.domain.VehicleStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class VehicleRepository implements PanacheRepositoryBase<Vehicle, UUID> {

    public Optional<Vehicle> findByCountryAndPlate(String countryCode, String plate) {
        return find("countryCode = ?1 and plate = ?2", countryCode, plate).firstResultOptional();
    }

    public List<Vehicle> pageByStatus(VehicleStatus status, int page, int size) {
        return find("status", Sort.ascending("updatedAt"), status).page(page, size).list();
    }

    public long countByStatus(VehicleStatus status) {
        return count("status", status);
    }
}
