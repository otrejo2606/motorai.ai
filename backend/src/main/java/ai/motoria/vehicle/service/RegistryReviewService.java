package ai.motoria.vehicle.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.MotoriaException;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.MediaReadUrl;
import ai.motoria.common.port.RegistryDocumentReaderPort;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.common.security.IdNumberHasher;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import ai.motoria.vehicle.domain.VehicleStatus;
import ai.motoria.vehicle.dto.RegistryReviewDetailResponse;
import ai.motoria.vehicle.dto.RegistryReviewSummaryResponse;
import ai.motoria.vehicle.dto.ReviewAttributeResponse;
import ai.motoria.vehicle.dto.ReviewPageResponse;
import ai.motoria.vehicle.event.VehicleEventProducer;
import ai.motoria.vehicle.exception.VehicleErrorCodes;
import ai.motoria.vehicle.mapper.VehicleMapper;
import ai.motoria.vehicle.repository.VehicleRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Backoffice tray of registry documents (design D6): the analyst sees the pages as images and the extracted values
 * with confidence and page, corrects values (source ANALYST, AI value kept) and approves or rejects.
 */
@ApplicationScoped
public class RegistryReviewService {

    static final String PAGE_PURPOSE = "REGISTRY_REVIEW";

    @Inject
    VehicleRepository vehicleRepository;

    @Inject
    VehicleService vehicleService;

    @Inject
    VehicleRegistrySettings settings;

    @Inject
    VehicleEventProducer vehicleEventProducer;

    @Inject
    VehicleMapper vehicleMapper;

    @Inject
    MediaPort mediaPort;

    @Inject
    RegistryDocumentReaderPort registryDocumentReader;

    @Inject
    IdNumberHasher idNumberHasher;

    @Inject
    CurrentUser currentUser;

    public PageResponse<RegistryReviewSummaryResponse> list(VehicleStatus status, int page, int size) {
        VehicleStatus effective = status == null ? VehicleStatus.REGISTRY_IN_REVIEW : status;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<RegistryReviewSummaryResponse> items = vehicleRepository.pageByStatus(effective, safePage, safeSize).stream()
                .map(vehicleMapper::toSummary)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, vehicleRepository.countByStatus(effective));
    }

    @Audited
    @Transactional
    public RegistryReviewDetailResponse detail(UUID vehicleId) {
        Vehicle vehicle = vehicleService.findVehicle(vehicleId);
        return vehicleMapper.toReviewDetail(vehicle, pages(vehicle));
    }

    @Audited
    @Transactional
    public ReviewAttributeResponse correctAttribute(UUID vehicleId, String key, String value) {
        Vehicle vehicle = findForReview(vehicleId);
        VehicleAttributeKey attributeKey = parseKey(key);
        String stored = attributeKey == VehicleAttributeKey.OWNER_ID_HASH ? idNumberHasher.hash(value) : value.trim();
        if (stored == null || stored.isBlank()) {
            throw new InvalidRequestException(VehicleErrorCodes.VEHICLE_ATTRIBUTE_UNKNOWN, "Invalid value");
        }
        try {
            vehicle.correctAttribute(attributeKey, stored, currentUser.id().toString(), Instant.now());
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(VehicleErrorCodes.VEHICLE_INVALID_STATE, "The vehicle is not in review");
        }
        return vehicle.findAttribute(attributeKey)
                .map(vehicleMapper::toReviewAttribute)
                .orElseThrow();
    }

    /**
     * Confirms all values: the vehicle becomes REGISTRY_VERIFIED, the transfer blocks are computed and
     * vehicle.registry.completed is published.
     */
    @Audited
    @Transactional
    public RegistryReviewSummaryResponse approve(UUID vehicleId) {
        Vehicle vehicle = findForReview(vehicleId);
        if (vehicle.getStatus() != VehicleStatus.REGISTRY_IN_REVIEW) {
            throw new InvalidStateException(VehicleErrorCodes.VEHICLE_INVALID_STATE, "The vehicle is not in review");
        }
        List<VehicleAttributeKey> missing = vehicle.missingAttributes(settings.requiredKeys());
        if (!missing.isEmpty()) {
            throw new BusinessRuleException(VehicleErrorCodes.VEHICLE_REQUIRED_ATTRIBUTES_MISSING,
                    "Required values are missing", missing.stream().map(Enum::name).toList());
        }
        boolean corrected = vehicle.hasAnalystCorrections();
        vehicle.approve(Instant.now());
        vehicleEventProducer.registryCompleted(vehicle);
        registryDocumentReader.recordHumanDecision(vehicle.getExtractionInferenceId(), currentUser.id().toString(),
                corrected ? "APPROVED_WITH_CORRECTIONS" : "APPROVED");
        return vehicleMapper.toSummary(vehicle);
    }

    @Audited
    @Transactional
    public RegistryReviewSummaryResponse reject(UUID vehicleId, String reason) {
        Vehicle vehicle = findForReview(vehicleId);
        try {
            vehicle.reject(reason.trim());
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(VehicleErrorCodes.VEHICLE_INVALID_STATE, "The vehicle is not in review");
        }
        registryDocumentReader.recordHumanDecision(vehicle.getExtractionInferenceId(), currentUser.id().toString(), "REJECTED");
        return vehicleMapper.toSummary(vehicle);
    }

    private Vehicle findForReview(UUID vehicleId) {
        Vehicle vehicle = vehicleService.findVehicle(vehicleId);
        if (vehicle.getRequestedBy().equals(currentUser.id())) {
            throw new ForbiddenOperationException(VehicleErrorCodes.VEHICLE_SELF_REVIEW_FORBIDDEN,
                    "An analyst cannot review a document they submitted");
        }
        return vehicle;
    }

    private VehicleAttributeKey parseKey(String key) {
        try {
            return VehicleAttributeKey.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRequestException(VehicleErrorCodes.VEHICLE_ATTRIBUTE_UNKNOWN, "Unknown attribute");
        }
    }

    private List<ReviewPageResponse> pages(Vehicle vehicle) {
        if (vehicle.getRegistryDocumentMediaId() == null) {
            return List.of();
        }
        try {
            List<UUID> pageIds = mediaPort.ensurePdfPageImages(vehicle.getRegistryDocumentMediaId());
            List<ReviewPageResponse> pages = new ArrayList<>(pageIds.size());
            for (int index = 0; index < pageIds.size(); index++) {
                MediaReadUrl url = mediaPort.createReadUrl(pageIds.get(index), PAGE_PURPOSE);
                pages.add(new ReviewPageResponse(index + 1, url.mediaId(), url.url(), url.expiresAt()));
            }
            return pages;
        } catch (MotoriaException exception) {
            Log.warnv("Registry pages not available for vehicle {0}: {1}", vehicle.getId(), exception.getCode());
            return List.of();
        }
    }
}
