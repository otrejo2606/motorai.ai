package ai.motoria.listing.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.common.port.PhotoSlotSnapshot;
import ai.motoria.common.port.UserIdentityPort;
import ai.motoria.common.port.UserIdentitySnapshot;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.listing.domain.Fuel;
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
 * {@link ListingPublicationPolicy}. Refreshes the trust level and the registry data of the listing on the way.
 */
@ApplicationScoped
public class ListingPublicationGuard {

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    UserIdentityPort userIdentityPort;

    @Inject
    MediaPort mediaPort;

    @Inject
    ListingPhotoReadingChecker readingChecker;

    public void assertPublishable(Listing listing) {
        Optional<VehicleSnapshot> vehicle = vehicleRegistryPort.findVehicle(listing.getVehicleId());
        boolean verified = vehicle.map(VehicleSnapshot::isRegistryVerified).orElse(false);
        if (verified) {
            // The draft can be created and get its photos before the registry lookup (add-consulta-credid-vehiculos),
            // so the registry data is copied again here, once the vehicle is verified.
            VehicleSnapshot snapshot = vehicle.get();
            listing.applyVehicleData(
                    snapshot.attribute("BRAND").orElse(null),
                    snapshot.attribute("MODEL").orElse(null),
                    ListingPersistenceService.parseYear(snapshot.attribute("YEAR").orElse(null)),
                    Fuel.fromRegistry(snapshot.attribute("FUEL").orElse(null)),
                    TrustLevel.REGISTRY_VERIFIED);
        } else {
            listing.updateTrustLevel(TrustLevel.NONE);
        }

        Optional<UserIdentitySnapshot> identity = userIdentityPort.findIdentity(listing.getSellerId());
        int identityRank = identity.map(UserIdentitySnapshot::identityRank).orElse(0);
        OwnershipMatch ownership = identity.map(UserIdentitySnapshot::idNumberHash)
                .map(hash -> vehicleRegistryPort.checkOwnership(listing.getVehicleId(), hash))
                .orElse(OwnershipMatch.UNKNOWN);
        List<String> requiredSlots = mediaPort.photoProtocol(listing.getCountryCode()).stream()
                .filter(PhotoSlotSnapshot::required)
                .map(PhotoSlotSnapshot::code)
                .toList();

        // The photos are usually taken before the registry lookup: what they read (VIN, odometer) is compared again here
        // with the registry VIN and the declared mileage (add-captura-guiada-fotos). Anomalies never block; a photo that
        // must be repeated (angle MISMATCH or rejected by the analyst) does.
        readingChecker.check(listing, vehicle.orElse(null));
        ListingPublicationPolicy.Result result = ListingPublicationPolicy.evaluate(
                listing.isContentComplete(), verified, identityRank, ownership, requiredSlots, listing.photoSlots(),
                listing.photoSlotsToRepeat());
        if (!result.publishable()) {
            List<String> details = new ArrayList<>(result.violations());
            details.addAll(result.missingSlots());
            throw new BusinessRuleException(result.violations().get(0), "The listing does not meet the publication rules", details);
        }
    }
}
