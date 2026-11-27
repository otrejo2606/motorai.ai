package ai.motoria.listing.service;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.domain.PhotoAngleStatus;
import ai.motoria.listing.domain.PhotoRejectionReason;
import ai.motoria.listing.domain.PhotoReviewDecision;
import ai.motoria.listing.dto.PhotoReviewRequest;
import ai.motoria.listing.event.ListingEventProducer;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingPhotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Analyst decision on the angle check (design D7 of add-captura-guiada-fotos).
 */
class ListingPhotoReviewServiceTest {

    private final ListingAccess listingAccess = mock(ListingAccess.class);
    private final ListingMapper listingMapper = mock(ListingMapper.class);
    private final ListingEventProducer eventProducer = mock(ListingEventProducer.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final ListingPhotoReviewService service = new ListingPhotoReviewService();
    private final UUID analystId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-27T15:00:00Z");
    private Listing listing;

    @BeforeEach
    void setUp() {
        service.listingAccess = listingAccess;
        service.listingMapper = listingMapper;
        service.listingEventProducer = eventProducer;
        service.currentUser = currentUser;
        service.listingPhotoRepository = mock(ListingPhotoRepository.class);
        listing = Listing.create(UUID.randomUUID(), UUID.randomUUID(), "CR", ListingCategory.CAR,
                "Toyota Corolla", "Un dueno", BigDecimal.valueOf(21000), "CRC", 40000);
        when(listingAccess.find(listing.getId())).thenReturn(listing);
        when(currentUser.id()).thenReturn(analystId);
    }

    @Test
    void analystShouldAcceptAnUncertainPhoto() {
        ListingPhoto photo = checkedPhoto(PhotoAngleStatus.UNCERTAIN);

        service.review(listing.getId(), "FRONT", new PhotoReviewRequest(PhotoReviewDecision.ACCEPTED, null));

        assertEquals(PhotoReviewDecision.ACCEPTED, photo.getReviewDecision());
        assertEquals(analystId, photo.getReviewedBy());
        verify(listingMapper).toReviewItem(photo);
    }

    @Test
    void rejectionOfAListingInReviewShouldSendItBackToDraft() {
        ListingPhoto photo = checkedPhoto(PhotoAngleStatus.UNAVAILABLE);
        listing.submitForReview();

        service.review(listing.getId(), "FRONT",
                new PhotoReviewRequest(PhotoReviewDecision.REJECTED, PhotoRejectionReason.TOO_DARK));

        assertEquals(PhotoReviewDecision.REJECTED, photo.getReviewDecision());
        assertEquals("TOO_DARK", photo.repeatReason());
        assertEquals(ListingStatus.DRAFT, listing.getStatus());
        verify(eventProducer).listingUpdated(listing);
    }

    @Test
    void rejectionWithoutReasonShouldBeInvalid() {
        checkedPhoto(PhotoAngleStatus.UNCERTAIN);

        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () -> service.review(
                listing.getId(), "FRONT", new PhotoReviewRequest(PhotoReviewDecision.REJECTED, null)));

        assertEquals(ListingErrorCodes.LISTING_PHOTO_REVIEW_REASON_REQUIRED, exception.getCode());
    }

    @Test
    void analystCannotReviewTheirOwnListing() {
        checkedPhoto(PhotoAngleStatus.UNCERTAIN);
        when(currentUser.id()).thenReturn(listing.getSellerId());

        ForbiddenOperationException exception = assertThrows(ForbiddenOperationException.class, () -> service.review(
                listing.getId(), "FRONT", new PhotoReviewRequest(PhotoReviewDecision.ACCEPTED, null)));

        assertEquals(ListingErrorCodes.LISTING_PHOTO_SELF_REVIEW_FORBIDDEN, exception.getCode());
    }

    @Test
    void clearMatchShouldNotBeReviewable() {
        checkedPhoto(PhotoAngleStatus.MATCH);

        InvalidStateException exception = assertThrows(InvalidStateException.class, () -> service.review(
                listing.getId(), "FRONT", new PhotoReviewRequest(PhotoReviewDecision.ACCEPTED, null)));

        assertEquals(ListingErrorCodes.LISTING_PHOTO_NOT_REVIEWABLE, exception.getCode());
    }

    @Test
    void publishedListingCannotBeReviewed() {
        checkedPhoto(PhotoAngleStatus.UNCERTAIN);
        listing.submitForReview();
        listing.publish();

        InvalidStateException exception = assertThrows(InvalidStateException.class, () -> service.review(
                listing.getId(), "FRONT", new PhotoReviewRequest(PhotoReviewDecision.ACCEPTED, null)));

        assertEquals(ListingErrorCodes.LISTING_INVALID_STATE, exception.getCode());
        verify(eventProducer, never()).listingUpdated(any());
    }

    private ListingPhoto checkedPhoto(PhotoAngleStatus status) {
        UUID mediaId = UUID.randomUUID();
        listing.attachPhoto("FRONT", mediaId, "https://cdn.example/1.jpg");
        ListingPhoto photo = listing.findPhoto("FRONT").orElseThrow();
        photo.applyAngleCheck(mediaId, status, "FRONT", 0.7, "v1", 10L, null, null, null, now);
        return photo;
    }
}
