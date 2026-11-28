package ai.motoria.listing.service;

import ai.motoria.common.port.VehicleAttributeSnapshot;
import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.domain.PhotoAngleStatus;
import ai.motoria.listing.domain.PhotoAnomaly;
import ai.motoria.listing.event.ListingEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Comparison of what the photos read with the vehicle and notification of new anomalies (design D6 of
 * add-captura-guiada-fotos). Fictitious data.
 */
class ListingPhotoReadingCheckerTest {

    private static final String REGISTRY_VIN = "JTDBR32E720123456";

    private final ListingEventProducer eventProducer = mock(ListingEventProducer.class);
    private final ListingPhotoReadingChecker checker = new ListingPhotoReadingChecker();
    private final Instant now = Instant.parse("2026-11-27T15:00:00Z");

    @BeforeEach
    void setUp() {
        checker.listingEventProducer = eventProducer;
        checker.toleranceKm = 500;
        checker.tolerancePercent = 5;
    }

    @Test
    void differentVinShouldBeRecordedAndNotifiedOnce() {
        Listing listing = listingWithReading("VIN", "JTDBR32E720999999", null);

        List<PhotoAnomaly> first = checker.check(listing, vehicle(REGISTRY_VIN));
        List<PhotoAnomaly> second = checker.check(listing, vehicle(REGISTRY_VIN));

        assertEquals(List.of(PhotoAnomaly.VIN_MISMATCH), first);
        assertTrue(second.isEmpty());
        assertEquals(PhotoAnomaly.VIN_MISMATCH, listing.findPhoto("VIN").orElseThrow().getReadingAnomaly());
        verify(eventProducer).photoAnomalyDetected(listing, List.of(PhotoAnomaly.VIN_MISMATCH));
    }

    @Test
    void vinShouldWaitForTheRegistryLookup() {
        Listing listing = listingWithReading("VIN", "JTDBR32E720999999", null);

        assertTrue(checker.check(listing, vehicle(null)).isEmpty());
        assertTrue(checker.check(listing, null).isEmpty());
        assertNull(listing.findPhoto("VIN").orElseThrow().getReadingAnomaly());
        verify(eventProducer, never()).photoAnomalyDetected(any(), any());
    }

    @Test
    void odometerFarFromTheDeclaredMileageShouldBeAnAnomaly() {
        Listing listing = listingWithReading("DASHBOARD_ODOMETER", null, 95000);

        assertEquals(List.of(PhotoAnomaly.ODOMETER_MISMATCH), checker.check(listing, vehicle(REGISTRY_VIN)));
    }

    @Test
    void odometerWithinToleranceShouldNotNotify() {
        Listing listing = listingWithReading("DASHBOARD_ODOMETER", null, 40800);

        assertTrue(checker.check(listing, vehicle(REGISTRY_VIN)).isEmpty());
        verify(eventProducer, never()).photoAnomalyDetected(any(), any());
    }

    private Listing listingWithReading(String slot, String vin, Integer odometerKm) {
        Listing listing = Listing.create(UUID.randomUUID(), UUID.randomUUID(), "CR", ListingCategory.CAR,
                "Toyota Corolla", "Un dueno", BigDecimal.valueOf(21000), "CRC", 40000);
        UUID mediaId = UUID.randomUUID();
        listing.attachPhoto(slot, mediaId, "https://cdn.example/1.jpg");
        ListingPhoto photo = listing.findPhoto(slot).orElseThrow();
        photo.applyAngleCheck(mediaId, PhotoAngleStatus.MATCH, slot, 0.95, "v3-ocr", 20L, null, vin, odometerKm, now);
        return listing;
    }

    private VehicleSnapshot vehicle(String vin) {
        List<VehicleAttributeSnapshot> attributes = vin == null
                ? List.of()
                : List.of(new VehicleAttributeSnapshot("VIN", vin, "REGISTRY_AGREEMENT", true));
        return new VehicleSnapshot(UUID.randomUUID(), "CR", "BCD123", "REGISTRY_VERIFIED", false, null, now, attributes);
    }
}
