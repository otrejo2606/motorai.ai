package ai.motoria.listing.service;

import ai.motoria.common.port.PhotoAngleCheckResult;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.domain.PhotoAngleStatus;
import ai.motoria.listing.mapper.ListingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Storing the angle check result on the listing photo (design D2 and D5 of add-captura-guiada-fotos).
 */
class ListingPhotoAngleApplierTest {

    private final ListingAccess listingAccess = mock(ListingAccess.class);
    private final VehicleRegistryPort vehicleRegistryPort = mock(VehicleRegistryPort.class);
    private final ListingMapper listingMapper = mock(ListingMapper.class);
    private final ListingPhotoReadingChecker readingChecker = mock(ListingPhotoReadingChecker.class);
    private final ListingPhotoAngleApplier applier = new ListingPhotoAngleApplier();
    private Listing listing;
    private UUID mediaId;

    @BeforeEach
    void setUp() {
        applier.listingAccess = listingAccess;
        applier.vehicleRegistryPort = vehicleRegistryPort;
        applier.listingMapper = listingMapper;
        applier.readingChecker = readingChecker;
        listing = Listing.create(UUID.randomUUID(), UUID.randomUUID(), "CR", ListingCategory.CAR,
                "Toyota Corolla", "Un dueno", BigDecimal.valueOf(21000), "CRC", 40000);
        mediaId = UUID.randomUUID();
        listing.attachPhoto("FRONT", mediaId, "https://cdn.example/1.jpg");
        when(listingAccess.find(listing.getId())).thenReturn(listing);
        when(vehicleRegistryPort.findVehicle(any())).thenReturn(Optional.empty());
    }

    @Test
    void mismatchToAnotherSlotShouldBeWrongAngle() {
        applier.apply(listing.getId(), "FRONT", mediaId,
                new PhotoAngleCheckResult("MISMATCH", "REAR", 0.97, "v1", 15L, null, null, null));

        ListingPhoto photo = listing.findPhoto("FRONT").orElseThrow();
        assertEquals(PhotoAngleStatus.MISMATCH, photo.getAngleStatus());
        assertEquals("REAR", photo.getDetectedSlot());
        assertEquals("WRONG_ANGLE", photo.repeatReason());
        verify(listingMapper).toResponse(listing, null, true);
        verify(readingChecker, never()).check(any(), any());
    }

    @Test
    void mismatchToOtherShouldBeNotTheVehicle() {
        applier.apply(listing.getId(), "FRONT", mediaId,
                new PhotoAngleCheckResult("MISMATCH", "OTHER", 0.97, "v1", 15L, null, null, null));

        assertEquals("NOT_THE_VEHICLE", listing.findPhoto("FRONT").orElseThrow().repeatReason());
    }

    @Test
    void unavailableShouldKeepTheClassifierCodeAndNotBlock() {
        applier.apply(listing.getId(), "FRONT", mediaId,
                PhotoAngleCheckResult.unavailable("PHOTO_CLASSIFIER_UNAVAILABLE", null));

        ListingPhoto photo = listing.findPhoto("FRONT").orElseThrow();
        assertEquals(PhotoAngleStatus.UNAVAILABLE, photo.getAngleStatus());
        assertEquals("PHOTO_CLASSIFIER_UNAVAILABLE", photo.getAngleReason());
        assertNull(photo.repeatReason());
    }

    @Test
    void notCheckedSlotShouldLeaveNoCheckDate() {
        applier.apply(listing.getId(), "FRONT", mediaId, PhotoAngleCheckResult.notChecked());

        ListingPhoto photo = listing.findPhoto("FRONT").orElseThrow();
        assertEquals(PhotoAngleStatus.NOT_CHECKED, photo.getAngleStatus());
        assertNull(photo.getAngleCheckedAt());
    }

    @Test
    void resultOfAReplacedPhotoShouldBeIgnored() {
        applier.apply(listing.getId(), "FRONT", UUID.randomUUID(),
                new PhotoAngleCheckResult("MISMATCH", "REAR", 0.97, "v1", 15L, null, null, null));

        assertEquals(PhotoAngleStatus.PENDING, listing.findPhoto("FRONT").orElseThrow().getAngleStatus());
    }

    @Test
    void readingsShouldBeComparedWithTheVehicle() {
        applier.apply(listing.getId(), "FRONT", mediaId,
                new PhotoAngleCheckResult("MATCH", "FRONT", 0.97, "v3-ocr", 15L, "JTDBR32E720123456", null, null));

        verify(readingChecker).check(listing, null);
    }
}
