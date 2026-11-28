package ai.motoria.listing.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.PriceRange;
import ai.motoria.listing.dto.CreateListingRequest;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.dto.UpdateListingRequest;
import ai.motoria.listing.event.ListingEventProducer;
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.exception.ListingNotFoundException;
import ai.motoria.listing.integration.AiPriceIntegration;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.List;
import java.util.Objects;
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
    ListingAccess listingAccess;

    @Inject
    ListingPublicationGuard listingPublicationGuard;

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    CurrentUser currentUser;

    @Audited
    public ListingResponse create(CreateListingRequest request) {
        CreateListingRequest sanitizedRequest = sanitize(request);
        PriceRange priceRange = aiPriceIntegration.recommendPriceRange(sanitizedRequest.price());
        UUID sellerId = currentUser.id();
        return listingPersistenceService.persistListing(sanitizedRequest, sellerId, priceRange);
    }

    /**
     * Public: only PUBLISHED. The owner and BACKOFFICE/ADMIN see any status (design D9.1 item 2).
     */
    @Transactional
    public ListingResponse getById(UUID listingId) {
        Listing listing = listingRepository.findByIdOptional(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        boolean privileged = listingAccess.isPrivilegedViewer(listing);
        if (!privileged && listing.getStatus() != ListingStatus.PUBLISHED) {
            throw new ListingNotFoundException(listingId);
        }
        return view(listing, privileged);
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
        Listing listing = listingAccess.find(listingId);
        listingAccess.assertOwner(listing);
        if (listing.getStatus() != ListingStatus.DRAFT) {
            throw new ListingInvalidStateException("Listing must be in state DRAFT but was " + listing.getStatus());
        }
        listingPublicationGuard.assertPublishable(listing);
        try {
            listing.submitForReview();
        } catch (IllegalStateException exception) {
            throw new ListingInvalidStateException(exception.getMessage());
        }
        listingEventProducer.listingUpdated(listing);
        return view(listing, true);
    }

    @Audited
    @Transactional
    public ListingResponse publish(UUID listingId) {
        Listing listing = listingAccess.find(listingId);
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new ListingInvalidStateException("Listing must be in state PENDING_REVIEW but was " + listing.getStatus());
        }
        listingPublicationGuard.assertPublishable(listing);
        try {
            listing.publish();
        } catch (IllegalStateException exception) {
            throw new ListingInvalidStateException(exception.getMessage());
        }
        listingEventProducer.listingPublished(listing);
        return view(listing, true);
    }

    @Audited
    @Transactional
    public ListingResponse markSold(UUID listingId) {
        Listing listing = listingAccess.find(listingId);
        listingAccess.assertOwner(listing);
        try {
            listing.markSold();
        } catch (IllegalStateException exception) {
            throw new ListingInvalidStateException(exception.getMessage());
        }
        listingEventProducer.listingSold(listing);
        return view(listing, true);
    }

    @Audited
    @Transactional
    public ListingResponse requestCertification(UUID listingId) {
        Listing listing = listingAccess.find(listingId);
        listingAccess.assertOwner(listing);
        listingEventProducer.certificationRequested(listing);
        return view(listing, true);
    }

    @Transactional
    public PageResponse<ListingSummaryResponse> listMine(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        PanacheQuery<Listing> query = listingRepository.findBySeller(currentUser.id(), safePage, safeSize);
        return toPage(query, safePage, safeSize);
    }

    @Transactional
    public PageResponse<ListingSummaryResponse> listForBackoffice(ListingStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        ListingStatus effective = status == null ? ListingStatus.PENDING_REVIEW : status;
        return toPage(listingRepository.findByStatus(effective, safePage, safeSize), safePage, safeSize);
    }

    private PageResponse<ListingSummaryResponse> toPage(PanacheQuery<Listing> query, int page, int size) {
        List<ListingSummaryResponse> items = query.list().stream().map(listingMapper::toSummary).toList();
        return new PageResponse<>(items, page, size, query.count());
    }

    private ListingResponse view(Listing listing, boolean privileged) {
        return listingMapper.toResponse(listing,
                vehicleRegistryPort.findVehicle(listing.getVehicleId()).orElse(null), privileged);
    }

    private CreateListingRequest sanitize(CreateListingRequest request) {
        return new CreateListingRequest(
                request.vehicleId(),
                request.price(),
                request.currency(),
                request.mileage(),
                request.province(),
                request.transmission(),
                sanitizeList(request.declaredEquipment()),
                sanitizeText(request.maintenanceHistory()),
                request.negotiable(),
                sanitizeText(request.title()),
                sanitizeText(request.description()));
    }

    private UpdateListingRequest sanitize(UpdateListingRequest request) {
        return new UpdateListingRequest(
                request.price(),
                request.currency(),
                request.mileage(),
                request.province(),
                request.transmission(),
                sanitizeList(request.declaredEquipment()),
                sanitizeText(request.maintenanceHistory()),
                request.negotiable(),
                sanitizeText(request.title()),
                sanitizeText(request.description()));
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::sanitizeText)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private String sanitizeText(String input) {
        return input == null ? null : PLAIN_TEXT_POLICY.sanitize(input).trim();
    }
}
