package ai.motoria.listing.domain;

import ai.motoria.common.domain.ListingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Angle check, readings and analyst decision on a listing photo (add-captura-guiada-fotos).
 */
class ListingPhotoTest {

    private final Instant now = Instant.parse("2026-11-27T15:00:00Z");

    @Test
    void attachedPhotoShouldStartPending() {
        Listing listing = newListing();
        UUID mediaId = UUID.randomUUID();

        listing.attachPhoto("FRONT", mediaId, "https://cdn.example/1.jpg");

        ListingPhoto photo = listing.findPhoto("FRONT").orElseThrow();
        assertEquals(PhotoAngleStatus.PENDING, photo.getAngleStatus());
        assertFalse(photo.mustBeRepeated());
    }

    @Test
    void resultShouldOnlyApplyToTheSamePendingPhoto() {
        Listing listing = newListing();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        listing.attachPhoto("FRONT", first, "https://cdn.example/1.jpg");
        listing.attachPhoto("FRONT", second, "https://cdn.example/2.jpg");
        ListingPhoto photo = listing.findPhoto("FRONT").orElseThrow();

        assertFalse(photo.applyAngleCheck(first, PhotoAngleStatus.MATCH, "FRONT", 0.9, "v1", 10L, null, null, null, now));
        assertTrue(photo.applyAngleCheck(second, PhotoAngleStatus.MATCH, "FRONT", 0.91234, "v1", 10L, null, null, null, now));
        assertFalse(photo.applyAngleCheck(second, PhotoAngleStatus.MISMATCH, "REAR", 0.99, "v1", 10L, null, null, null, now));

        assertEquals(PhotoAngleStatus.MATCH, photo.getAngleStatus());
        assertEquals(new BigDecimal("0.9123"), photo.getAngleConfidence());
        assertEquals("v1", photo.getAngleModelVersion());
        assertEquals(now, photo.getAngleCheckedAt());
    }

    @Test
    void mismatchShouldBeRepeatedWithItsReason() {
        ListingPhoto photo = checkedPhoto(PhotoAngleStatus.MISMATCH, "REAR", PhotoRejectionReason.WRONG_ANGLE.name());

        assertTrue(photo.mustBeRepeated());
        assertEquals("WRONG_ANGLE", photo.repeatReason());
        assertTrue(photo.isReviewable());
        assertFalse(photo.awaitsAnalyst());
    }

    @Test
    void uncertainAndUnavailableShouldGoToTheAnalystWithoutBlocking() {
        ListingPhoto uncertain = checkedPhoto(PhotoAngleStatus.UNCERTAIN, "FRONT", null);
        ListingPhoto unavailable = checkedPhoto(PhotoAngleStatus.UNAVAILABLE, null, "PHOTO_CLASSIFIER_UNAVAILABLE");

        assertTrue(uncertain.awaitsAnalyst());
        assertTrue(unavailable.awaitsAnalyst());
        assertFalse(uncertain.mustBeRepeated());
        assertFalse(unavailable.mustBeRepeated());
        assertNull(unavailable.repeatReason());
    }

    @Test
    void analystAcceptanceShouldClearAMismatch() {
        ListingPhoto photo = checkedPhoto(PhotoAngleStatus.MISMATCH, "OTHER", PhotoRejectionReason.NOT_THE_VEHICLE.name());

        photo.review(PhotoReviewDecision.ACCEPTED, null, UUID.randomUUID(), now);

        assertFalse(photo.mustBeRepeated());
        assertFalse(photo.isReviewable());
        assertNull(photo.getReviewReason());
    }

    @Test
    void analystRejectionShouldBlockWithTheAnalystReason() {
        ListingPhoto photo = checkedPhoto(PhotoAngleStatus.UNCERTAIN, "FRONT", null);

        photo.review(PhotoReviewDecision.REJECTED, PhotoRejectionReason.BLURRY, UUID.randomUUID(), now);

        assertTrue(photo.mustBeRepeated());
        assertEquals("BLURRY", photo.repeatReason());
        assertFalse(photo.awaitsAnalyst());
    }

    @Test
    void rejectionWithoutReasonShouldFail() {
        ListingPhoto photo = checkedPhoto(PhotoAngleStatus.UNCERTAIN, "FRONT", null);

        assertThrows(NullPointerException.class,
                () -> photo.review(PhotoReviewDecision.REJECTED, null, UUID.randomUUID(), now));
    }

    @Test
    void matchWithoutAnomalyShouldNotBeReviewable() {
        assertFalse(checkedPhoto(PhotoAngleStatus.MATCH, "FRONT", null).isReviewable());
    }

    @Test
    void readingAnomalyShouldBeNewOnlyOnce() {
        ListingPhoto photo = checkedPhoto(PhotoAngleStatus.MATCH, "FRONT", null);

        assertTrue(photo.recordReadingAnomaly(PhotoAnomaly.VIN_MISMATCH));
        assertFalse(photo.recordReadingAnomaly(PhotoAnomaly.VIN_MISMATCH));
        assertTrue(photo.awaitsAnalyst());
        assertFalse(photo.recordReadingAnomaly(null));
        assertNull(photo.getReadingAnomaly());
    }

    @Test
    void replacingThePhotoShouldClearTheCheckAndTheDecision() {
        Listing listing = newListing();
        UUID first = UUID.randomUUID();
        listing.attachPhoto("VIN", first, "https://cdn.example/1.jpg");
        ListingPhoto photo = listing.findPhoto("VIN").orElseThrow();
        photo.applyAngleCheck(first, PhotoAngleStatus.MISMATCH, "OTHER", 0.98, "v1", 10L, "NOT_THE_VEHICLE",
                "JTDBR32E720123456", null, now);
        photo.recordReadingAnomaly(PhotoAnomaly.VIN_MISMATCH);
        photo.review(PhotoReviewDecision.REJECTED, PhotoRejectionReason.OTHER, UUID.randomUUID(), now);

        listing.attachPhoto("VIN", UUID.randomUUID(), "https://cdn.example/2.jpg");

        assertEquals(PhotoAngleStatus.PENDING, photo.getAngleStatus());
        assertNull(photo.getReadVin());
        assertNull(photo.getReadingAnomaly());
        assertNull(photo.getReviewDecision());
        assertFalse(photo.mustBeRepeated());
    }

    @Test
    void listingShouldListTheSlotsToRepeatAndReturnToDraft() {
        Listing listing = newListing();
        UUID front = UUID.randomUUID();
        listing.attachPhoto("FRONT", front, "https://cdn.example/1.jpg");
        listing.attachPhoto("REAR", UUID.randomUUID(), "https://cdn.example/2.jpg");
        listing.findPhoto("FRONT").orElseThrow()
                .applyAngleCheck(front, PhotoAngleStatus.MISMATCH, "REAR", 0.95, "v1", 10L, "WRONG_ANGLE", null, null, now);

        assertEquals(List.of("FRONT"), listing.photoSlotsToRepeat());

        listing.submitForReview();
        listing.returnToDraft();
        assertEquals(ListingStatus.DRAFT, listing.getStatus());
        assertThrows(IllegalStateException.class, listing::returnToDraft);
    }

    private ListingPhoto checkedPhoto(PhotoAngleStatus status, String detected, String reason) {
        Listing listing = newListing();
        UUID mediaId = UUID.randomUUID();
        listing.attachPhoto("FRONT", mediaId, "https://cdn.example/1.jpg");
        ListingPhoto photo = listing.findPhoto("FRONT").orElseThrow();
        photo.applyAngleCheck(mediaId, status, detected, status == PhotoAngleStatus.UNAVAILABLE ? null : 0.95, "v1", 10L,
                reason, null, null, now);
        return photo;
    }

    private Listing newListing() {
        return Listing.create(UUID.randomUUID(), UUID.randomUUID(), "CR", ListingCategory.CAR,
                "Toyota Corolla", "Un dueno", BigDecimal.valueOf(21000), "CRC", 40000);
    }
}
