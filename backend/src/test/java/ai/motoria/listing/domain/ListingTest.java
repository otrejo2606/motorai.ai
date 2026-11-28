package ai.motoria.listing.domain;

import ai.motoria.common.domain.ListingStatus;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals(TrustLevel.NONE, listing.getTrustLevel());
        assertNotNull(listing.getCreatedAt());
        assertNotNull(listing.getUpdatedAt());
        assertEquals(listing.getCreatedAt(), listing.getUpdatedAt());
    }

    @Test
    void createShouldAcceptEmptyTitleAndDescriptionInDraft() {
        Listing listing = Listing.create(UUID.randomUUID(), UUID.randomUUID(), "CR", ListingCategory.CAR,
                null, null, BigDecimal.valueOf(20000), "CRC", 15000);

        assertEquals("", listing.getTitle());
        assertEquals("", listing.getDescription());
        assertFalse(listing.isContentComplete());
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
        assertNotNull(listing.getPublishedAt());
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
    void updateDeclaredDataShouldRefreshValuesAndTimestamp() throws InterruptedException {
        Listing listing = newListing();
        Instant originalUpdatedAt = listing.getUpdatedAt();

        Thread.sleep(5);
        listing.updateDeclaredData("Updated title", "Updated description", BigDecimal.valueOf(21000), "USD", 9000,
                Province.HEREDIA, Transmission.AUTOMATIC, List.of("Sunroof"), "Agency service", true);

        assertEquals("Updated title", listing.getTitle());
        assertEquals("Updated description", listing.getDescription());
        assertEquals(BigDecimal.valueOf(21000), listing.getPrice());
        assertEquals("USD", listing.getCurrency());
        assertEquals(9000, listing.getMileage());
        assertEquals(Province.HEREDIA, listing.getProvince());
        assertEquals(Transmission.AUTOMATIC, listing.getTransmission());
        assertEquals(List.of("Sunroof"), listing.getDeclaredEquipment());
        assertTrue(listing.isNegotiable());
        assertTrue(listing.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void applyVehicleDataShouldPrefillRegistryFields() {
        Listing listing = newListing();

        listing.applyVehicleData("TOYOTA", "COROLLA", 2021, Fuel.GASOLINE, TrustLevel.REGISTRY_VERIFIED);

        assertEquals("TOYOTA", listing.getBrand());
        assertEquals("COROLLA", listing.getModel());
        assertEquals(2021, listing.getModelYear());
        assertEquals(Fuel.GASOLINE, listing.getFuel());
        assertEquals(TrustLevel.REGISTRY_VERIFIED, listing.getTrustLevel());
    }

    @Test
    void photosShouldBeReplacedBySlotAndRemoved() {
        Listing listing = newListing();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        listing.attachPhoto("FRONT", first, "https://cdn.example/1.jpg");
        listing.attachPhoto("FRONT", second, "https://cdn.example/2.jpg");

        assertEquals(1, listing.getPhotos().size());
        assertEquals(second, listing.findPhoto("FRONT").orElseThrow().getMediaId());
        assertTrue(listing.removePhoto("FRONT"));
        assertFalse(listing.removePhoto("FRONT"));
    }

    private Listing newListing() {
        return Listing.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "CR",
                ListingCategory.CAR,
                "Title",
                "Description",
                BigDecimal.valueOf(20000),
                "CRC",
                15000);
    }
}
