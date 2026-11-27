package ai.motoria.listing.service;

import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.domain.PhotoAnomaly;
import ai.motoria.listing.domain.PhotoReadingPolicy;
import ai.motoria.listing.event.ListingEventProducer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Compares what the photos read (VIN, odometer) with the registry VIN and the declared mileage (design D6 of
 * add-captura-guiada-fotos) and, for every new anomaly, publishes vehicle.registry.anomaly.detected so the seller is
 * told. Runs after the angle check and again when the listing goes to review or is published, because the photos are
 * usually taken before the registry lookup. A photo is only compared when both values exist. Caller must be inside a
 * transaction.
 */
@ApplicationScoped
public class ListingPhotoReadingChecker {

    static final String VIN_ATTRIBUTE = "VIN";

    @Inject
    ListingEventProducer listingEventProducer;

    @ConfigProperty(name = "motoria.listing.photo.odometer-tolerance-km", defaultValue = "500")
    int toleranceKm;

    @ConfigProperty(name = "motoria.listing.photo.odometer-tolerance-percent", defaultValue = "5")
    int tolerancePercent;

    /**
     * @return the anomalies that are new on this call (already published)
     */
    public List<PhotoAnomaly> check(Listing listing, VehicleSnapshot vehicle) {
        String registryVin = vehicle == null ? null : vehicle.attribute(VIN_ATTRIBUTE).orElse(null);
        Integer declaredMileage = listing.getMileage();
        List<PhotoAnomaly> detected = new ArrayList<>();
        for (ListingPhoto photo : listing.getPhotos()) {
            if (!comparable(photo, registryVin, declaredMileage)) {
                continue;
            }
            Optional<PhotoAnomaly> anomaly = PhotoReadingPolicy.compare(photo.getSlot(), photo.getReadVin(),
                    photo.getReadOdometerKm(), registryVin, declaredMileage, toleranceKm, tolerancePercent);
            if (photo.recordReadingAnomaly(anomaly.orElse(null))) {
                detected.add(anomaly.get());
            }
        }
        if (!detected.isEmpty()) {
            listingEventProducer.photoAnomalyDetected(listing, detected);
        }
        return detected;
    }

    private static boolean comparable(ListingPhoto photo, String registryVin, Integer declaredMileage) {
        if (PhotoReadingPolicy.VIN_SLOT.equals(photo.getSlot())) {
            return photo.getReadVin() != null && registryVin != null && !registryVin.isBlank();
        }
        if (PhotoReadingPolicy.ODOMETER_SLOT.equals(photo.getSlot())) {
            return photo.getReadOdometerKm() != null && declaredMileage != null;
        }
        return false;
    }
}
