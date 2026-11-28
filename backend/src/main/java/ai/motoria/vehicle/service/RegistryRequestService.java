package ai.motoria.vehicle.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.vehicle.domain.RegistryRequest;
import ai.motoria.vehicle.domain.RegistryRequestStatus;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.dto.RegistryRequestResponse;
import ai.motoria.vehicle.exception.VehicleErrorCodes;
import ai.motoria.vehicle.mapper.VehicleMapper;
import ai.motoria.vehicle.repository.RegistryRequestRepository;
import ai.motoria.vehicle.repository.VehicleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "Que Motoria lo obtenga" (design D1 step 3): the seller accepts the configured fee, a task appears in the
 * backoffice, and the vehicle stays in REGISTRY_PENDING until the operator uploads the report.
 * Charging the fee is outside F1 (owner gate 0.5).
 */
@ApplicationScoped
public class RegistryRequestService {

    @Inject
    RegistryRequestRepository registryRequestRepository;

    @Inject
    VehicleRepository vehicleRepository;

    @Inject
    VehicleService vehicleService;

    @Inject
    VehicleRegistrySettings settings;

    @Inject
    VehicleMapper vehicleMapper;

    @Inject
    CurrentUser currentUser;

    @Audited
    @Transactional
    public RegistryRequestResponse create(UUID vehicleId, BigDecimal acceptedFee) {
        BigDecimal fee = settings.registryRequestFee()
                .orElseThrow(() -> new BusinessRuleException(VehicleErrorCodes.REGISTRY_REQUEST_NOT_AVAILABLE,
                        "This option is not available yet"));
        if (acceptedFee.compareTo(fee) != 0) {
            throw new BusinessRuleException(VehicleErrorCodes.REGISTRY_REQUEST_FEE_MISMATCH,
                    "The accepted fee does not match the current fee");
        }
        Vehicle vehicle = vehicleService.findVehicle(vehicleId);
        if (registryRequestRepository.existsOpen(vehicleId)) {
            throw new InvalidStateException(VehicleErrorCodes.REGISTRY_REQUEST_ALREADY_OPEN, "A request is already open");
        }
        try {
            vehicle.awaitRegistryRequest();
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(VehicleErrorCodes.VEHICLE_INVALID_STATE,
                    "The vehicle already has a registry document");
        }
        RegistryRequest request = RegistryRequest.open(vehicleId, currentUser.id(), fee, settings.registryRequestCurrency());
        try {
            registryRequestRepository.persistAndFlush(request);
        } catch (PersistenceException exception) {
            if (UniqueViolations.isUniqueViolation(exception)) {
                throw new InvalidStateException(VehicleErrorCodes.REGISTRY_REQUEST_ALREADY_OPEN, "A request is already open");
            }
            throw exception;
        }
        return vehicleMapper.toResponse(request, vehicle);
    }

    public PageResponse<RegistryRequestResponse> list(RegistryRequestStatus status, int page, int size) {
        RegistryRequestStatus effective = status == null ? RegistryRequestStatus.PENDING : status;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<RegistryRequestResponse> items = registryRequestRepository.pageByStatus(effective, safePage, safeSize).stream()
                .map(request -> vehicleMapper.toResponse(request,
                        vehicleRepository.findByIdOptional(request.getVehicleId()).orElse(null)))
                .toList();
        return new PageResponse<>(items, safePage, safeSize, registryRequestRepository.countByStatus(effective));
    }

    /**
     * The operator uploaded the registry PDF (media owned by the operator); the vehicle goes to REGISTRY_IN_REVIEW.
     */
    @Audited
    @Transactional
    public RegistryRequestResponse fulfill(UUID requestId, UUID mediaId) {
        UUID operatorId = currentUser.id();
        RegistryRequest request = registryRequestRepository.findByIdOptional(requestId)
                .orElseThrow(() -> new NotFoundException(VehicleErrorCodes.REGISTRY_REQUEST_NOT_FOUND, "Request not found"));
        if (request.getStatus() != RegistryRequestStatus.PENDING) {
            throw new InvalidStateException(VehicleErrorCodes.REGISTRY_REQUEST_INVALID_STATE, "The request is not pending");
        }
        vehicleService.requireRegistryDocument(mediaId, operatorId);
        Vehicle vehicle = vehicleService.findVehicle(request.getVehicleId());
        vehicleService.attach(vehicle, mediaId, request.getRequestedBy());
        request.fulfill(mediaId, operatorId.toString(), Instant.now());
        return vehicleMapper.toResponse(request, vehicle);
    }
}
