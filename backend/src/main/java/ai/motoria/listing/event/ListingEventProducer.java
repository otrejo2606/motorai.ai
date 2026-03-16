package ai.motoria.listing.event;

import ai.motoria.common.event.DomainEventPublisher;
import ai.motoria.common.event.EventType;
import ai.motoria.listing.domain.Listing;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
                        listing.getVehicleSpecId(),
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

    public void certificationRequested(Listing listing) {
        eventPublisher.publish(
                EventType.LISTING_CERTIFICATION_REQUESTED,
                MODULE,
                listing.getId(),
                new ListingStatusPayload(listing.getId(), listing.getStatus()));
    }
}
