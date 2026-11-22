package ai.motoria.vehicle.domain;

import ai.motoria.common.port.ExtractedEncumbrance;
import ai.motoria.common.port.ExtractedValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleTest {

    private static final Duration MAX_AGE = Duration.ofDays(30);

    @Test
    void registerShouldStartPending() {
        Vehicle vehicle = Vehicle.register("CR", "ABC123", UUID.randomUUID());

        assertEquals(VehicleStatus.REGISTRY_PENDING, vehicle.getStatus());
        assertFalse(vehicle.isTransferBlocked());
    }

    @Test
    void attachingDocumentShouldMoveToInReview() {
        Vehicle vehicle = Vehicle.register("CR", "ABC123", UUID.randomUUID());
        UUID mediaId = UUID.randomUUID();

        vehicle.attachRegistryDocument(mediaId, UUID.randomUUID(), Instant.now(), MAX_AGE);

        assertEquals(VehicleStatus.REGISTRY_IN_REVIEW, vehicle.getStatus());
        assertTrue(vehicle.isAwaitingExtraction(mediaId));
    }

    @Test
    void attachingTwiceShouldFail() {
        Vehicle vehicle = inReview();

        assertThrows(IllegalStateException.class,
                () -> vehicle.attachRegistryDocument(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), MAX_AGE));
    }

    @Test
    void approveShouldVerifyAndComputeTransferBlockFromPledge() {
        Vehicle vehicle = inReview();
        vehicle.applyExtraction(UUID.randomUUID(), values(), List.of(
                        new ExtractedEncumbrance("PLEDGE", "Prenda a favor de Banco X", 2, 0.95),
                        new ExtractedEncumbrance("OTHER", "Nota", 2, 0.9)),
                LocalDate.now(), ReviewReason.MANUAL_REVIEW_DEFAULT, Instant.now());

        vehicle.approve(Instant.now());

        assertEquals(VehicleStatus.REGISTRY_VERIFIED, vehicle.getStatus());
        assertTrue(vehicle.isTransferBlocked());
        assertEquals("PLEDGE", vehicle.getTransferBlockReason());
    }

    @Test
    void approveWithoutEncumbrancesShouldNotBlock() {
        Vehicle vehicle = inReview();
        vehicle.applyExtraction(UUID.randomUUID(), values(), List.of(), LocalDate.now(),
                ReviewReason.MANUAL_REVIEW_DEFAULT, Instant.now());

        vehicle.approve(Instant.now());

        assertFalse(vehicle.isTransferBlocked());
        assertNull(vehicle.getTransferBlockReason());
    }

    @Test
    void analystCorrectionShouldKeepAiValue() {
        Vehicle vehicle = inReview();
        vehicle.applyExtraction(UUID.randomUUID(), values(), List.of(), LocalDate.now(),
                ReviewReason.LOW_CONFIDENCE, Instant.now());

        vehicle.correctAttribute(VehicleAttributeKey.YEAR, "2020", "analyst-1", Instant.now());

        VehicleAttribute year = vehicle.findAttribute(VehicleAttributeKey.YEAR).orElseThrow();
        assertEquals("2020", year.getValue());
        assertEquals("2021", year.getAiValue());
        assertEquals(AttributeSource.ANALYST, year.getSource());
        assertEquals("analyst-1", year.getCorrectedBy());
        assertTrue(vehicle.hasAnalystCorrections());
    }

    @Test
    void missingAttributesShouldListRequiredKeysNotPresent() {
        Vehicle vehicle = inReview();
        vehicle.applyExtraction(UUID.randomUUID(), List.of(new ExtractedValue("BRAND", "TOYOTA", 1, 0.99)), List.of(),
                LocalDate.now(), ReviewReason.MISSING_REQUIRED, Instant.now());

        assertEquals(List.of(VehicleAttributeKey.MODEL, VehicleAttributeKey.VIN),
                vehicle.missingAttributes(List.of(VehicleAttributeKey.BRAND, VehicleAttributeKey.MODEL, VehicleAttributeKey.VIN)));
    }

    @Test
    void rejectedVehicleCanReceiveANewDocument() {
        Vehicle vehicle = inReview();
        vehicle.requireReviewWithoutData(UUID.randomUUID(), ReviewReason.PLATE_MISMATCH);
        vehicle.reject("Documento de otra placa");

        assertEquals(VehicleStatus.REGISTRY_REJECTED, vehicle.getStatus());
        assertTrue(vehicle.canReceiveRegistryDocument(Instant.now(), MAX_AGE));
    }

    @Test
    void verifiedVehicleRejectsDocumentUntilStale() {
        Vehicle vehicle = inReview();
        vehicle.applyExtraction(UUID.randomUUID(), values(), List.of(), LocalDate.now(),
                ReviewReason.MANUAL_REVIEW_DEFAULT, Instant.now());
        vehicle.approve(Instant.now());

        assertFalse(vehicle.canReceiveRegistryDocument(Instant.now(), MAX_AGE));
        assertTrue(vehicle.canReceiveRegistryDocument(Instant.now().plus(Duration.ofDays(40)), MAX_AGE));
    }

    @Test
    void registryRequestShouldReopenRejectedVehicle() {
        Vehicle vehicle = inReview();
        vehicle.reject("Ilegible");

        vehicle.awaitRegistryRequest();

        assertEquals(VehicleStatus.REGISTRY_PENDING, vehicle.getStatus());
    }

    private Vehicle inReview() {
        Vehicle vehicle = Vehicle.register("CR", "ABC123", UUID.randomUUID());
        vehicle.attachRegistryDocument(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), MAX_AGE);
        return vehicle;
    }

    private List<ExtractedValue> values() {
        return List.of(
                new ExtractedValue("BRAND", "TOYOTA", 1, 0.99),
                new ExtractedValue("MODEL", "COROLLA", 1, 0.98),
                new ExtractedValue("YEAR", "2021", 1, 0.97),
                new ExtractedValue("VIN", "JTDBR32E720123456", 1, 0.96),
                new ExtractedValue("OWNER_ID_HASH", "abc", 1, 0.95));
    }
}
