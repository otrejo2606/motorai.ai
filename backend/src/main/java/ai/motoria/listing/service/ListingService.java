package ai.motoria.listing.service;

import ai.motoria.common.audit.Audited;
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
import ai.motoria.listing.integration.AiPriceIntegration;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.UUID;

@ApplicationScoped
public class ListingService {

    private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

    @Inject
    ListingRepository listingRepository;

    @Inject
    ListingMapper listingMapper;

    @Inject
    AiPriceIntegration aiPriceIntegration;

    @Inject
    ListingEventProducer listingEventProducer;

    @Inject
    ListingPersistenceService listingPersistenceService;

    @Inject
    JsonWebToken jwt;

    @Audited
    public ListingResponse create(CreateListingRequest request) {
        CreateListingRequest sanitizedRequest = sanitize(request);
        PriceRange priceRange = aiPriceIntegration.recommendPriceRange(sanitizedRequest.price());
        UUID sellerId = UUID.fromString(jwt.getSubject());
        return listingPersistenceService.persistListing(sanitizedRequest, sellerId, priceRange);
    }

    public ListingResponse getById(UUID listingId) {
        return listingMapper.toResponse(findById(listingId));
    }

    @Audited
    public ListingResponse update(UUID listingId, UpdateListingRequest request) {
        UpdateListingRequest sanitizedRequest = sanitize(request);
        PriceRange priceRange = aiPriceIntegration.recommendPriceRange(sanitizedRequest.price());
        return listingPersistenceService.applyUpdate(listingId, sanitizedRequest, priceRange);
    }

    @Audited
    @Transactional
    public ListingResponse submitForReview(UUID listingId) {
        Listing listing = findById(listingId);
        assertOwnership(listing);
        try {
            listing.submitForReview();
        } catch (IllegalStateException exception) {
            throw new ListingInvalidStateException(exception.getMessage());
        }
        listingEventProducer.listingUpdated(listing);
        return listingMapper.toResponse(listing);
    }

    @Audited
    @Transactional
    public ListingResponse publish(UUID listingId) {
        Listing listing = findById(listingId);
        try {
            listing.publish();
        } catch (IllegalStateException exception) {
            throw new ListingInvalidStateException(exception.getMessage());
        }
        listingEventProducer.listingPublished(listing);
        return listingMapper.toResponse(listing);
    }

    @Audited
    @Transactional
    public ListingResponse markSold(UUID listingId) {
        Listing listing = findById(listingId);
        assertOwnership(listing);
        try {
            listing.markSold();
        } catch (IllegalStateException exception) {
            throw new ListingInvalidStateException(exception.getMessage());
        }
        listingEventProducer.listingSold(listing);
        return listingMapper.toResponse(listing);
    }

    @Audited
    @Transactional
    public ListingResponse requestCertification(UUID listingId) {
        Listing listing = findById(listingId);
        assertOwnership(listing);
        listingEventProducer.certificationRequested(listing);
        return listingMapper.toResponse(listing);
    }

    private CreateListingRequest sanitize(CreateListingRequest request) {
        return new CreateListingRequest(
                sanitizeText(request.title()),
                sanitizeText(request.description()),
                request.category(),
                request.vehicleSpecId(),
                request.price(),
                request.modelYear(),
                request.mileage());
    }

    private UpdateListingRequest sanitize(UpdateListingRequest request) {
        return new UpdateListingRequest(
                sanitizeText(request.title()),
                sanitizeText(request.description()),
                request.price(),
                request.modelYear(),
                request.mileage());
    }

    private String sanitizeText(String input) {
        return PLAIN_TEXT_POLICY.sanitize(input).trim();
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