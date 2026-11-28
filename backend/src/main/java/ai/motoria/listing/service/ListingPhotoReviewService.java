package ai.motoria.listing.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.domain.PhotoReviewDecision;
import ai.motoria.listing.dto.PhotoReviewItemResponse;
import ai.motoria.listing.dto.PhotoReviewRequest;
import ai.motoria.listing.event.ListingEventProducer;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingPhotoRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Analyst review of the photo angle check (design D7 of add-captura-guiada-fotos): tray of UNCERTAIN and UNAVAILABLE
 * photos and of reading anomalies, and the decision ACCEPTED or REJECTED. The analyst never reviews a listing they sell.
 * Rejecting a photo of a listing waiting for review sends it back to DRAFT so the seller can repeat it.
 */
@ApplicationScoped
public class ListingPhotoReviewService {

    @Inject
    ListingPhotoRepository listingPhotoRepository;

    @Inject
    ListingAccess listingAccess;

    @Inject
    ListingMapper listingMapper;

    @Inject
    ListingEventProducer listingEventProducer;

    @Inject
    CurrentUser currentUser;

    @Transactional
    public PageResponse<PhotoReviewItemResponse> pending(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PanacheQuery<ListingPhoto> query = listingPhotoRepository.findAwaitingAnalyst(currentUser.id(), safePage, safeSize);
        List<PhotoReviewItemResponse> items = query.list().stream().map(listingMapper::toReviewItem).toList();
        return new PageResponse<>(items, safePage, safeSize, query.count());
    }

    @Audited
    @Transactional
    public PhotoReviewItemResponse review(UUID listingId, String slot, PhotoReviewRequest request) {
        Listing listing = listingAccess.find(listingId);
        UUID reviewer = currentUser.id();
        if (reviewer.equals(listing.getSellerId())) {
            throw new ForbiddenOperationException(ListingErrorCodes.LISTING_PHOTO_SELF_REVIEW_FORBIDDEN,
                    "An analyst cannot review the photos of a listing they sell");
        }
        if (listing.getStatus() != ListingStatus.DRAFT && listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new ListingInvalidStateException("Photos can only be reviewed while the listing is DRAFT or PENDING_REVIEW");
        }
        ListingPhoto photo = listing.findPhoto(slot)
                .orElseThrow(() -> new NotFoundException(ListingErrorCodes.LISTING_PHOTO_NOT_FOUND, "No photo in this slot"));
        if (!photo.isReviewable()) {
            throw new InvalidStateException(ListingErrorCodes.LISTING_PHOTO_NOT_REVIEWABLE,
                    "This photo has no angle check to decide on");
        }
        if (request.decision() == PhotoReviewDecision.REJECTED && request.reason() == null) {
            throw new InvalidRequestException(ListingErrorCodes.LISTING_PHOTO_REVIEW_REASON_REQUIRED,
                    "A reason is required to reject a photo");
        }
        photo.review(request.decision(), request.reason(), reviewer, Instant.now());
        if (request.decision() == PhotoReviewDecision.REJECTED && listing.getStatus() == ListingStatus.PENDING_REVIEW) {
            listing.returnToDraft();
            listingEventProducer.listingUpdated(listing);
        }
        return listingMapper.toReviewItem(photo);
    }
}
