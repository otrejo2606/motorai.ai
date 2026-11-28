package ai.motoria.vehicle.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.MediaSnapshot;
import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.common.port.UserIdentityPort;
import ai.motoria.common.port.UserIdentitySnapshot;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.vehicle.domain.OwnershipPolicy;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import ai.motoria.vehicle.dto.FeeResponse;
import ai.motoria.vehicle.dto.RegisterVehicleRequest;
import ai.motoria.vehicle.dto.VehicleResponse;
import ai.motoria.vehicle.event.VehicleEventProducer;
import ai.motoria.vehicle.exception.VehicleErrorCodes;
import ai.motoria.vehicle.mapper.VehicleMapper;
import ai.motoria.vehicle.repository.RegistryRequestRepository;
import ai.motoria.vehicle.repository.VehicleRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Seller flow (design D1 steps 2 and 3): register or reuse a vehicle by plate and attach the registry document.
 */
@ApplicationScoped
public class VehicleService {

    static final String REGISTRY_DOCUMENT_KIND = "REGISTRY_DOCUMENT";

    @Inject
    VehicleRepository vehicleRepository;

    @Inject
    VehicleCreator vehicleCreator;

    @Inject
    RegistryRequestRepository registryRequestRepository;

    @Inject
    PlateNormalizer plateNormalizer;

    @Inject
    VehicleRegistrySettings settings;

    @Inject
    VehicleEventProducer vehicleEventProducer;

    @Inject
    VehicleMapper vehicleMapper;

    @Inject
    MediaPort mediaPort;

    @Inject
    UserIdentityPort userIdentityPort;

    @Inject
    CurrentUser currentUser;

    /**
     * Returns the existing vehicle for the country and plate, or creates it in REGISTRY_PENDING.
     */
    @Audited
    @Transactional
    public VehicleResponse register(RegisterVehicleRequest request) {
        String country = request.countryCode().toUpperCase(Locale.ROOT);
        if (!plateNormalizer.isSupported(country)) {
            throw new InvalidRequestException(VehicleErrorCodes.VEHICLE_COUNTRY_UNSUPPORTED, "Country not supported");
        }
        String plate = plateNormalizer.normalize(country, request.plate())
                .orElseThrow(() -> new InvalidRequestException(VehicleErrorCodes.VEHICLE_PLATE_INVALID,
                        "The plate does not match the format of the country"));
        Vehicle vehicle = vehicleRepository.findByCountryAndPlate(country, plate)
                .orElseGet(() -> createOrLoad(country, plate));
        return toResponse(vehicle);
    }

    /**
     * Creates the vehicle in its own transaction; if a concurrent request created the same plate first, re-reads it.
     */
    Vehicle createOrLoad(String country, String plate) {
        try {
            UUID createdId = vehicleCreator.create(country, plate, currentUser.id());
            return vehicleRepository.findByIdOptional(createdId)
                    .orElseThrow(this::concurrentCreationFailed);
        } catch (PersistenceException exception) {
            if (!UniqueViolations.isUniqueViolation(exception)) {
                throw exception;
            }
            return vehicleRepository.findByCountryAndPlate(country, plate)
                    .orElseThrow(this::concurrentCreationFailed);
        }
    }

    private InvalidStateException concurrentCreationFailed() {
        return new InvalidStateException(VehicleErrorCodes.VEHICLE_CONCURRENT_CREATION,
                "The vehicle could not be created; try again");
    }

    @Transactional
    public VehicleResponse get(UUID vehicleId) {
        return toResponse(findVehicle(vehicleId));
    }

    /**
     * The vehicle goes to REGISTRY_IN_REVIEW and the extraction runs in the background (vehicle.registry.requested).
     */
    @Audited
    @Transactional
    public VehicleResponse attachRegistryDocument(UUID vehicleId, UUID mediaId) {
        UUID callerId = currentUser.id();
        Vehicle vehicle = findVehicle(vehicleId);
        requireRegistryDocument(mediaId, callerId);
        attach(vehicle, mediaId, callerId);
        return toResponse(vehicle);
    }

    public void attach(Vehicle vehicle, UUID mediaId, UUID requester) {
        try {
            vehicle.attachRegistryDocument(mediaId, requester, Instant.now(), settings.documentMaxAge(),
                    settings.agreementMaxAge());
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(VehicleErrorCodes.VEHICLE_INVALID_STATE,
                    "The vehicle cannot receive a registry document now");
        }
        vehicleEventProducer.registryRequested(vehicle);
    }

    /**
     * The document must be a confirmed registry PDF uploaded by the caller.
     */
    public void requireRegistryDocument(UUID mediaId, UUID ownerId) {
        MediaSnapshot media = mediaPort.findMedia(mediaId)
                .orElseThrow(() -> new BusinessRuleException(VehicleErrorCodes.VEHICLE_DOCUMENT_INVALID, "Unknown document"));
        boolean valid = media.ownerId().equals(ownerId)
                && REGISTRY_DOCUMENT_KIND.equals(media.kind())
                && media.isConfirmed();
        if (!valid) {
            throw new BusinessRuleException(VehicleErrorCodes.VEHICLE_DOCUMENT_INVALID,
                    "The document must be a confirmed registry PDF uploaded by the caller");
        }
    }

    public Vehicle findVehicle(UUID vehicleId) {
        return vehicleRepository.findByIdOptional(vehicleId)
                .orElseThrow(() -> new NotFoundException(VehicleErrorCodes.VEHICLE_NOT_FOUND, "Vehicle not found"));
    }

    public VehicleResponse toResponse(Vehicle vehicle) {
        FeeResponse fee = settings.registryRequestFee()
                .map(amount -> new FeeResponse(amount, settings.registryRequestCurrency()))
                .orElse(null);
        String ownerHash = vehicle.attributeValue(VehicleAttributeKey.OWNER_ID_HASH).orElse(null);
        String callerHash = currentUser.optionalId()
                .flatMap(userIdentityPort::findIdentity)
                .map(UserIdentitySnapshot::idNumberHash)
                .orElse(null);
        OwnershipMatch ownerMatch = OwnershipPolicy.compare(ownerHash, callerHash);
        return vehicleMapper.toResponse(vehicle, fee, registryRequestRepository.existsOpen(vehicle.getId()), ownerMatch);
    }
}
