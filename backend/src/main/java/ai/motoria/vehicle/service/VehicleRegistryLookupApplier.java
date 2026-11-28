package ai.motoria.vehicle.service;

import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.port.OwnershipMatch;
import ai.motoria.common.port.UserIdentityPort;
import ai.motoria.common.port.UserIdentitySnapshot;
import ai.motoria.vehicle.domain.OwnershipPolicy;
import ai.motoria.vehicle.domain.RegistryAnomaly;
import ai.motoria.vehicle.domain.RegistryAnomalyPolicy;
import ai.motoria.vehicle.domain.RegistryLookup;
import ai.motoria.vehicle.domain.RegistryLookupOutcome;
import ai.motoria.vehicle.domain.RegistryLookupPurpose;
import ai.motoria.vehicle.domain.RegistryLookupStatus;
import ai.motoria.vehicle.domain.ReviewReason;
import ai.motoria.vehicle.domain.Vehicle;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import ai.motoria.vehicle.domain.VehicleStatus;
import ai.motoria.vehicle.event.VehicleEventProducer;
import ai.motoria.vehicle.exception.VehicleErrorCodes;
import ai.motoria.vehicle.integration.VehicleRegistryRecord;
import ai.motoria.vehicle.repository.RegistryLookupRepository;
import ai.motoria.vehicle.repository.VehicleRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional steps around the (non transactional, possibly slow) call to the vehicle registry, like
 * {@link RegistryExtractionApplier}: prepare decides whether a paid query is needed, apply stores the answer.
 */
@ApplicationScoped
public class VehicleRegistryLookupApplier {

    static final String SUPPORTED_COUNTRY = "CR";

    @Inject
    VehicleRepository vehicleRepository;

    @Inject
    RegistryLookupRepository registryLookupRepository;

    @Inject
    VehicleRegistrySettings settings;

    @Inject
    VehicleEventProducer vehicleEventProducer;

    @Inject
    UserIdentityPort userIdentityPort;

    /**
     * @param plate  normalized plate to send to the registry; null when no call is needed
     * @param result final result when no call is needed (reused data, unsupported country, quota exhausted)
     */
    public record Prepared(String plate, RegistryLookupResult result) {

        public boolean callsProvider() {
            return result == null;
        }
    }

    /**
     * A verified vehicle whose data is still fresh (90 days for agreement data, 30 for a document) is not queried
     * again unless {@code force} (transfer). A vehicle waiting for the analyst cannot be queried.
     */
    @Transactional
    public Prepared prepare(UUID vehicleId, UUID requester, RegistryLookupPurpose purpose, boolean force, String provider) {
        Vehicle vehicle = find(vehicleId);
        if (vehicle.getStatus() == VehicleStatus.REGISTRY_IN_REVIEW) {
            throw new InvalidStateException(VehicleErrorCodes.VEHICLE_INVALID_STATE,
                    "The registry data of the vehicle is waiting for the analyst");
        }
        Instant now = Instant.now();
        if (!force && vehicle.getStatus() == VehicleStatus.REGISTRY_VERIFIED
                && !vehicle.isRegistryStale(now, settings.documentMaxAge(), settings.agreementMaxAge())) {
            return new Prepared(null, new RegistryLookupResult(RegistryLookupStatus.REUSED, null,
                    reportAnomalies(vehicle, requester, purpose)));
        }
        if (!SUPPORTED_COUNTRY.equals(vehicle.getCountryCode())) {
            return new Prepared(null, RegistryLookupResult.failed(RegistryLookupStatus.NOT_CONFIGURED,
                    VehicleErrorCodes.VEHICLE_REGISTRY_NOT_CONFIGURED));
        }
        if (registryLookupRepository.countAnsweredSince(provider, startOfToday()) >= settings.dailyQuota()) {
            return new Prepared(null, RegistryLookupResult.failed(RegistryLookupStatus.QUOTA_EXHAUSTED,
                    VehicleErrorCodes.VEHICLE_REGISTRY_QUOTA_EXHAUSTED));
        }
        return new Prepared(vehicle.getPlate(), null);
    }

    /**
     * The registry could not answer: the call is logged with its code and the vehicle stays as it was.
     */
    @Transactional
    public RegistryLookupResult recordFailure(UUID vehicleId, String provider, String plateClass,
                                              RegistryLookupPurpose purpose, UUID requester, String errorCode) {
        registryLookupRepository.persist(RegistryLookup.record(vehicleId, provider, purpose, RegistryLookupOutcome.FAILED,
                errorCode, plateClass, null, requester, Instant.now()));
        RegistryLookupStatus status = VehicleErrorCodes.VEHICLE_REGISTRY_NOT_CONFIGURED.equals(errorCode)
                ? RegistryLookupStatus.NOT_CONFIGURED
                : RegistryLookupStatus.UNAVAILABLE;
        return RegistryLookupResult.failed(status, errorCode);
    }

    /**
     * Logs the call and, when the plate was found, stores the agreement data: automatic approval when every required
     * value is present and motoria.vehicle.registry.agreement-auto-approve is on, otherwise the analyst reviews it.
     *
     * @param ownerIdHash keyed hash of the registry owner identity number, null when absent or not computable
     */
    @Transactional
    public RegistryLookupResult apply(UUID vehicleId, String provider, String plateClass, RegistryLookupPurpose purpose,
                                      UUID requester, VehicleRegistryRecord record, String ownerIdHash) {
        Vehicle vehicle = find(vehicleId);
        Instant now = Instant.now();
        registryLookupRepository.persist(RegistryLookup.record(vehicleId, provider, purpose,
                record.found() ? RegistryLookupOutcome.FOUND : RegistryLookupOutcome.NOT_FOUND,
                null, plateClass, record.providerReference(), requester, now));
        if (!record.found()) {
            return RegistryLookupResult.of(RegistryLookupStatus.NOT_FOUND);
        }
        if (!vehicle.canApplyAgreementLookup()) {
            Log.infov("Registry agreement data not applied to vehicle {0}: it is now {1}", vehicleId, vehicle.getStatus());
            return RegistryLookupResult.of(RegistryLookupStatus.FOUND);
        }

        Map<VehicleAttributeKey, String> values = new EnumMap<>(VehicleAttributeKey.class);
        values.putAll(record.attributes());
        values.remove(VehicleAttributeKey.OWNER_ID_HASH);
        values.remove(VehicleAttributeKey.OWNER_NAME);
        if (record.ownerName() != null && !record.ownerName().isBlank()) {
            values.put(VehicleAttributeKey.OWNER_NAME, record.ownerName());
        }
        if (ownerIdHash != null && !ownerIdHash.isBlank()) {
            values.put(VehicleAttributeKey.OWNER_ID_HASH, ownerIdHash);
        }
        List<VehicleAttributeKey> required = settings.requiredKeys();
        boolean complete = required.stream().allMatch(values::containsKey);
        ReviewReason reason = complete ? ReviewReason.AGREEMENT_REVIEW : ReviewReason.MISSING_REQUIRED;
        UUID effectiveRequester = requester != null ? requester : vehicle.getRequestedBy();

        vehicle.applyAgreementLookup(values, record.pledged(), pledgeDescription(record), effectiveRequester, reason, now);
        if (complete && settings.agreementAutoApprove() && vehicle.missingAttributes(required).isEmpty()) {
            vehicle.approve(now);
            vehicleEventProducer.registryCompleted(vehicle);
        } else {
            vehicleEventProducer.registryReviewRequired(vehicle, null);
        }
        return new RegistryLookupResult(RegistryLookupStatus.FOUND, null, reportAnomalies(vehicle, requester, purpose));
    }

    /**
     * Compares the registry data with the seller who asked for it and, when something does not fit, publishes
     * vehicle.registry.anomaly.detected so the seller is notified. Only for the seller flow: the transfer (F5) will
     * decide what to do with its own lookups.
     */
    List<RegistryAnomaly> reportAnomalies(Vehicle vehicle, UUID requester, RegistryLookupPurpose purpose) {
        if (purpose != RegistryLookupPurpose.SELLER_LOOKUP || requester == null) {
            return List.of();
        }
        String sellerHash = userIdentityPort.findIdentity(requester)
                .map(UserIdentitySnapshot::idNumberHash)
                .orElse(null);
        OwnershipMatch ownership = OwnershipPolicy.compare(
                vehicle.attributeValue(VehicleAttributeKey.OWNER_ID_HASH).orElse(null), sellerHash);
        List<RegistryAnomaly> anomalies = RegistryAnomalyPolicy.detect(ownership, vehicle.getEncumbrances());
        if (!anomalies.isEmpty()) {
            vehicleEventProducer.registryAnomalyDetected(vehicle, requester, anomalies);
        }
        return anomalies;
    }

    /**
     * Only the registry datum (total pledged as reported), never a fixed sentence: the type is translated by the UI.
     */
    static String pledgeDescription(VehicleRegistryRecord record) {
        return record.pledged() ? record.pledgeAmount() : null;
    }

    private Vehicle find(UUID vehicleId) {
        return vehicleRepository.findByIdOptional(vehicleId)
                .orElseThrow(() -> new NotFoundException(VehicleErrorCodes.VEHICLE_NOT_FOUND, "Vehicle not found"));
    }

    private Instant startOfToday() {
        ZoneId zone = settings.quotaZone();
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }
}
