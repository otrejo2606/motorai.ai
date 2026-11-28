package ai.motoria.listing.event;

import ai.motoria.common.event.DomainEventPublisher;
import ai.motoria.common.event.EventType;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.PhotoAnomaly;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ListingEventProducer {

    private static final String MODULE = "listing";

    @Inject
    DomainEventPublisher eventPublisher;

    public void listingCreated(Listing listing) {
        eventPublisher.publish(
                EventType.LISTING_CREATED,
                MODULE,
                listing.getId(),
                new ListingCreatedPayload(
                        listing.getId(),
                        listing.getSellerId(),
                        listing.getVehicleId(),
                        listing.getStatus(),
                        listing.getPrice()));
    }

    public void listingUpdated(Listing listing) {
        eventPublisher.publish(
                EventType.LISTING_UPDATED,
                MODULE,
                listing.getId(),
                new ListingStatusPayload(listing.getId(), listing.getStatus()));
    }

    public void listingPublished(Listing listing) {
        eventPublisher.publish(
                EventType.LISTING_PUBLISHED,
                MODULE,
                listing.getId(),
                new ListingStatusPayload(listing.getId(), listing.getStatus()));
    }

    public void listingSold(Listing listing) {
        eventPublisher.publish(
                EventType.LISTING_SOLD,
                MODULE,
                listing.getId(),
                new ListingStatusPayload(listing.getId(), listing.getStatus()));
    }

    /**
     * A photo reads something that does not fit the vehicle (add-captura-guiada-fotos): the existing registry anomaly
     * event is reused so the notification module tells the seller. The aggregate is the vehicle, as in that event.
     */
    public void photoAnomalyDetected(Listing listing, List<PhotoAnomaly> anomalies) {
        eventPublisher.publish(
                EventType.VEHICLE_REGISTRY_ANOMALY_DETECTED,
                MODULE,
                listing.getVehicleId(),
                new ListingPhotoAnomalyPayload(
                        listing.getVehicleId(),
                        listing.getSellerId(),
                        anomalies.stream().map(Enum::name).toList(),
                        listing.getId()));
    }

    public void certificationRequested(Listing listing) {
        eventPublisher.publish(
                EventType.LISTING_CERTIFICATION_REQUESTED,
                MODULE,
                listing.getId(),
                new ListingStatusPayload(listing.getId(), listing.getStatus()));
    }
}
