package ai.motoria.vehicle.repository;

import ai.motoria.vehicle.domain.RegistryRequest;
import ai.motoria.vehicle.domain.RegistryRequestStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RegistryRequestRepository implements PanacheRepositoryBase<RegistryRequest, UUID> {

    public boolean existsOpen(UUID vehicleId) {
        return count("vehicleId = ?1 and status = ?2", vehicleId, RegistryRequestStatus.PENDING) > 0;
    }

    public List<RegistryRequest> pageByStatus(RegistryRequestStatus status, int page, int size) {
        return find("status", Sort.ascending("createdAt"), status).page(page, size).list();
    }

    public long countByStatus(RegistryRequestStatus status) {
        return count("status", status);
    }
}
