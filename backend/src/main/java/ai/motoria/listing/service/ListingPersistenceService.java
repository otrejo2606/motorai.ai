package ai.motoria.listing.service;

import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.listing.domain.Listing;
import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.domain.PriceRange;
import ai.motoria.listing.dto.CreateListingRequest;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.UpdateListingRequest;
import ai.motoria.listing.event.ListingEventProducer;
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.exception.ListingNotFoundException;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@ApplicationScoped
class ListingPersistenceService {

    @Inject
    ListingRepository listingRepository;

    @Inject
    ListingMapper listingMapper;

    @Inject
    ListingEventProducer listingEventProducer;

    @Inject
    JsonWebToken jwt;

    @Transactional
    public ListingResponse persistListing(CreateListingRequest request, UUID sellerId, PriceRange priceRange) {
        Listing listing = Listing.create(
                sellerId,
                request.vehicleSpecId(),
                request.category(),
                request.title(),
                request.description(),
                request.price(),
                request.modelYear(),
                request.mileage());
        listing.applyRecommendedPriceRange(priceRange);
        listingRepository.persist(listing);
        listingEventProducer.listingCreated(listing);
        return listingMapper.toResponse(listing);
    }

    @Transactional
    public ListingResponse applyUpdate(UUID listingId, UpdateListingRequest request, PriceRange priceRange) {
        Listing listing = findById(listingId);
        assertOwnership(listing);
        if (listing.getStatus() == ListingStatus.SOLD) {
            throw new ListingInvalidStateException("Sold listings cannot be updated");
        }
        listing.updateDetails(request.title(), request.description(), request.price(), request.modelYear(), request.mileage());
        listing.applyRecommendedPriceRange(priceRange);
        listingEventProducer.listingUpdated(listing);
        return listingMapper.toResponse(listing);
    }

    private void assertOwnership(Listing listing) {
        UUID callerId = UUID.fromString(jwt.getSubject());
        if (!listing.getSellerId().equals(callerId)) {
            throw new ForbiddenOperationException("Access denied to listing " + listing.getId());
        }
    }

    private Listing findById(UUID listingId) {
        return listingRepository.findByIdOptional(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
    }
}