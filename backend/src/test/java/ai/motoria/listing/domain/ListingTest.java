package ai.motoria.listing.domain;

import ai.motoria.common.domain.ListingStatus;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingTest {

    @Test
    void createShouldInitializeListingInDraftState() {
        Listing listing = newListing();

        assertNotNull(listing.getId());
        assertEquals(ListingStatus.DRAFT, listing.getStatus());
        assertEquals(ListingCategory.CAR, listing.getCategory());
        assertNotNull(listing.getCreatedAt());
        assertNotNull(listing.getUpdatedAt());
        assertEquals(listing.getCreatedAt(), listing.getUpdatedAt());
    }

    @Test
    void submitForReviewShouldMoveDraftToPendingReview() {
        Listing listing = newListing();

        listing.submitForReview();

        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    @Test
    void publishShouldMovePendingReviewToPublished() {
        Listing listing = newListing();
        listing.submitForReview();

        listing.publish();

        assertEquals(ListingStatus.PUBLISHED, listing.getStatus());
    }

    @Test
    void markSoldShouldMovePublishedToSold() {
        Listing listing = newListing();
        listing.submitForReview();
        listing.publish();

        listing.markSold();

        assertEquals(ListingStatus.SOLD, listing.getStatus());
    }

    @Test
    void invalidTransitionsShouldThrowIllegalStateException() {
        Listing draftListing = newListing();
        assertThrows(IllegalStateException.class, draftListing::publish);
        assertThrows(IllegalStateException.class, draftListing::markSold);

        Listing publishedListing = newListing();
        publishedListing.submitForReview();
        publishedListing.publish();
        assertThrows(IllegalStateException.class, publishedListing::submitForReview);
    }

    @Test
    void updateDetailsShouldRefreshValuesAndTimestamp() throws InterruptedException {
        Listing listing = newListing();
        Instant originalUpdatedAt = listing.getUpdatedAt();

        Thread.sleep(5);
        listing.updateDetails("Updated title", "Updated description", BigDecimal.valueOf(21000), 2022, 9000);

        assertEquals("Updated title", listing.getTitle());
        assertEquals("Updated description", listing.getDescription());
        assertEquals(BigDecimal.valueOf(21000), listing.getPrice());
        assertEquals(2022, listing.getModelYear());
        assertEquals(9000, listing.getMileage());
        assertTrue(listing.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    private Listing newListing() {
        return Listing.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ListingCategory.CAR,
                "Title",
                "Description",
                BigDecimal.valueOf(20000),
                2021,
                15000);
    }
}