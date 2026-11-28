package ai.motoria.vehicle.service;

import ai.motoria.common.port.ExtractedValue;
import ai.motoria.common.port.RegistryDocumentReaderPort;
import ai.motoria.common.port.RegistryExtraction;
import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.domain.VehicleStatus;
import ai.motoria.vehicle.event.VehicleEventProducer;
import ai.motoria.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Applying an extraction with the AI result simulated: different plate, confidence threshold and auto approval.
 */
class RegistryExtractionApplierTest {

    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final VehicleEventProducer vehicleEventProducer = mock(VehicleEventProducer.class);
    private final RegistryDocumentReaderPort registryDocumentReader = mock(RegistryDocumentReaderPort.class);
    private RegistryExtractionApplier applier;
    private VehicleRegistrySettings settings;
    private Vehicle vehicle;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        settings = new VehicleRegistrySettings();
        settings.requiredAttributes = List.of("BRAND", "MODEL", "YEAR");
        settings.minConfidence = 0.85;
        settings.documentMaxAge = Duration.ofDays(30);
        settings.autoApproveEnabled = false;
        settings.registryRequestFee = Optional.empty();
        settings.registryRequestCurrency = "CRC";

        applier = new RegistryExtractionApplier();
        applier.vehicleRepository = vehicleRepository;
        applier.settings = settings;
        applier.plateNormalizer = PlateNormalizer.of(Map.of("CR", "([0-9]{1,6}|[A-Z]{1,3}[0-9]{1,6})"));
        applier.vehicleEventProducer = vehicleEventProducer;
        applier.registryDocumentReader = registryDocumentReader;

        vehicle = Vehicle.register("CR", "ABC123", UUID.randomUUID());
        documentId = UUID.randomUUID();
        vehicle.attachRegistryDocument(documentId, UUID.randomUUID(), Instant.now(), Duration.ofDays(30));
        when(vehicleRepository.findByIdOptional(vehicle.getId())).thenReturn(Optional.of(vehicle));
    }

    @Test
    void differentPlateShouldApplyNoDataAndRequireReview() {
        RegistryExtraction extraction = extraction("XYZ-999", 0.99);

        applier.apply(vehicle.getId(), documentId, extraction);

        assertTrue(vehicle.getAttributes().isEmpty());
        assertEquals(ReviewReason.PLATE_MISMATCH, vehicle.getReviewReason());
        assertEquals(VehicleStatus.REGISTRY_IN_REVIEW, vehicle.getStatus());
        verify(vehicleEventProducer).registryReviewRequired(vehicle, extraction.inferenceId());
        verify(vehicleEventProducer, never()).registryCompleted(any());
    }

    @Test
    void lowConfidenceShouldApplyValuesAndRequireReview() {
        applier.apply(vehicle.getId(), documentId, extraction("ABC-123", 0.40));

        assertEquals(3, vehicle.getAttributes().size());
        assertEquals(ReviewReason.LOW_CONFIDENCE, vehicle.getReviewReason());
        verify(vehicleEventProducer).registryReviewRequired(eq(vehicle), any());
    }

    @Test
    void cleanExtractionGoesToAnalystWhileAutoApprovalIsOff() {
        applier.apply(vehicle.getId(), documentId, extraction("ABC123", 0.99));

        assertEquals(ReviewReason.MANUAL_REVIEW_DEFAULT, vehicle.getReviewReason());
        assertEquals(VehicleStatus.REGISTRY_IN_REVIEW, vehicle.getStatus());
        verify(vehicleEventProducer, never()).registryCompleted(any());
    }

    @Test
    void cleanExtractionIsVerifiedWhenAutoApprovalIsOn() {
        settings.autoApproveEnabled = true;
        RegistryExtraction extraction = extraction("ABC123", 0.99);

        applier.apply(vehicle.getId(), documentId, extraction);

        assertEquals(VehicleStatus.REGISTRY_VERIFIED, vehicle.getStatus());
        verify(vehicleEventProducer).registryCompleted(vehicle);
        verify(registryDocumentReader).recordHumanDecision(eq(extraction.inferenceId()), any(), eq("AUTO_APPROVED"));
    }

    @Test
    void otherDocumentShouldBeIgnored() {
        applier.apply(vehicle.getId(), UUID.randomUUID(), extraction("ABC123", 0.99));

        assertTrue(vehicle.getAttributes().isEmpty());
        verifyNoInteractions(vehicleEventProducer);
    }

    private RegistryExtraction extraction(String plate, double brandConfidence) {
        return new RegistryExtraction(UUID.randomUUID(), true, null,
                new ExtractedValue("DOCUMENT_PLATE", plate, 1, 0.99),
                LocalDate.now(ZoneOffset.UTC),
                List.of(
                        new ExtractedValue("BRAND", "TOYOTA", 1, brandConfidence),
                        new ExtractedValue("MODEL", "COROLLA", 1, 0.99),
                        new ExtractedValue("YEAR", "2021", 1, 0.99)),
                List.of(), 0, 2);
    }
}
