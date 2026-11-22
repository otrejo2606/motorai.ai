package ai.motoria.listing.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.common.port.PhotoSlotSnapshot;
import ai.motoria.common.port.UserIdentityPort;
import ai.motoria.common.port.UserIdentitySnapshot;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingPublicationPolicy;
import ai.motoria.listing.domain.TrustLevel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gathers the vehicle, identity and photo protocol through the shared ports and applies
 * {@link ListingPublicationPolicy}. Refreshes the trust level on the way.
 */
@ApplicationScoped
public class ListingPublicationGuard {

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    UserIdentityPort userIdentityPort;

    @Inject
    MediaPort mediaPort;

    public void assertPublishable(Listing listing) {
        Optional<VehicleSnapshot> vehicle = vehicleRegistryPort.findVehicle(listing.getVehicleId());
        boolean verified = vehicle.map(VehicleSnapshot::isRegistryVerified).orElse(false);
        listing.updateTrustLevel(verified ? TrustLevel.REGISTRY_VERIFIED : TrustLevel.NONE);

        Optional<UserIdentitySnapshot> identity = userIdentityPort.findIdentity(listing.getSellerId());
        int identityRank = identity.map(UserIdentitySnapshot::identityRank).orElse(0);
        OwnershipMatch ownership = identity.map(UserIdentitySnapshot::idNumberHash)
                .map(hash -> vehicleRegistryPort.checkOwnership(listing.getVehicleId(), hash))
                .orElse(OwnershipMatch.UNKNOWN);
        List<String> requiredSlots = mediaPort.photoProtocol(listing.getCountryCode()).stream()
                .filter(PhotoSlotSnapshot::required)
                .map(PhotoSlotSnapshot::code)
                .toList();

        ListingPublicationPolicy.Result result = ListingPublicationPolicy.evaluate(
                listing.isContentComplete(), verified, identityRank, ownership, requiredSlots, listing.photoSlots());
        if (!result.publishable()) {
            List<String> details = new ArrayList<>(result.violations());
            details.addAll(result.missingSlots());
            throw new BusinessRuleException(result.violations().get(0), "The listing does not meet the publication rules", details);
        }
    }
}
