package ai.motoria.listing.service;

import ai.motoria.common.persistence.AuditSubject;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Reacts to vehicle.registry.completed: listings of that vehicle get the REGISTRY_VERIFIED trust level.
 */
@ApplicationScoped
public class ListingTrustService {

    @Inject
    ListingRepository listingRepository;

    @Transactional
    public int markVehicleVerified(UUID vehicleId) {
        return listingRepository.markVehicleVerified(vehicleId, AuditSubject.SYSTEM);
    }
}
