package ai.motoria.vehicle.service;

import ai.motoria.common.port.RegistryDocumentReaderPort;
import ai.motoria.common.port.RegistryExtraction;
import ai.motoria.common.port.RegistryExtractionRequest;
import ai.motoria.common.persistence.AuditSubject;
import ai.motoria.vehicle.domain.ReviewDecision;
import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.event.VehicleEventProducer;
import ai.motoria.vehicle.repository.VehicleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional steps around the (non transactional, possibly slow) model call.
 */
@ApplicationScoped
public class RegistryExtractionApplier {

    @Inject
    VehicleRepository vehicleRepository;

    @Inject
    VehicleRegistrySettings settings;

    @Inject
    PlateNormalizer plateNormalizer;

    @Inject
    VehicleEventProducer vehicleEventProducer;

    @Inject
    RegistryDocumentReaderPort registryDocumentReader;

    /**
     * Returns the extraction request only when the vehicle is still waiting for this document (idempotent consumer).
     */
    @Transactional
    public Optional<RegistryExtractionRequest> prepare(UUID vehicleId, UUID documentMediaId, UUID correlationId) {
        return vehicleRepository.findByIdOptional(vehicleId)
                .filter(vehicle -> vehicle.isAwaitingExtraction(documentMediaId))
                .map(vehicle -> new RegistryExtractionRequest(vehicleId, documentMediaId, vehicle.getCountryCode(), correlationId));
    }

    @Transactional
    public void apply(UUID vehicleId, UUID documentMediaId, RegistryExtraction extraction) {
        Vehicle vehicle = vehicleRepository.findByIdOptional(vehicleId).orElse(null);
        if (vehicle == null || !vehicle.isAwaitingExtraction(documentMediaId)) {
            return;
        }
        String country = vehicle.getCountryCode();
        ReviewDecision decision = settings.reviewPolicy().decide(
                extraction,
                vehicle.getPlate(),
                plate -> plateNormalizer.normalize(country, plate),
                LocalDate.now(ZoneOffset.UTC));
        Instant now = Instant.now();
        if (decision.applyData()) {
            vehicle.applyExtraction(extraction.inferenceId(), extraction.attributes(), extraction.encumbrances(),
                    extraction.documentDate(),
                    decision.autoApprove() ? ReviewReason.MANUAL_REVIEW_DEFAULT : decision.reason(), now);
        } else {
            vehicle.requireReviewWithoutData(extraction.inferenceId(), decision.reason());
        }

        if (decision.autoApprove() && vehicle.missingAttributes(settings.requiredKeys()).isEmpty()) {
            vehicle.approve(now);
            vehicleEventProducer.registryCompleted(vehicle);
            registryDocumentReader.recordHumanDecision(extraction.inferenceId(), AuditSubject.SYSTEM, "AUTO_APPROVED");
        } else {
            vehicleEventProducer.registryReviewRequired(vehicle, extraction.inferenceId());
        }
    }
}
