package ai.motoria.vehicle.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.common.security.IdNumberHasher;
import ai.motoria.vehicle.domain.RegistryLookupPurpose;
import ai.motoria.vehicle.dto.RegistryLookupResponse;
import ai.motoria.vehicle.exception.VehicleErrorCodes;
import ai.motoria.vehicle.integration.CredidPlateClass;
import ai.motoria.vehicle.integration.VehicleRegistryLookupPort;
import ai.motoria.vehicle.integration.VehicleRegistryRecord;
import ai.motoria.vehicle.integration.VehicleRegistryUnavailableException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Registry lookup by plate (Credid), first option of the seller flow; the registry PDF stays as the fallback.
 * No transaction surrounds the call to the provider (prepare and apply run in their own transactions). A failure of
 * the registry never becomes an HTTP error: the result says why and the vehicle stays as it was.
 * The plate and the owner identity number are never logged; the owner number is hashed before anything is stored.
 */
@ApplicationScoped
public class VehicleRegistryLookupService {

    @Inject
    VehicleRegistryLookupApplier applier;

    @Inject
    VehicleRegistryLookupPort registryLookupPort;

    @Inject
    IdNumberHasher idNumberHasher;

    @Inject
    VehicleService vehicleService;

    @Inject
    CurrentUser currentUser;

    /**
     * Seller lookup: reuses fresh registry data, otherwise queries the registry and applies the answer.
     */
    @Audited
    public RegistryLookupResponse lookup(UUID vehicleId) {
        RegistryLookupResult result = run(vehicleId, currentUser.id(), RegistryLookupPurpose.SELLER_LOOKUP, false);
        return new RegistryLookupResponse(result.status(), result.errorCode(), result.anomalies(),
                vehicleService.get(vehicleId));
    }

    /**
     * Hook for the transfer (F5, not built yet): ignores the 90 day reuse and queries the registry again, before signing
     * (TRANSFER_BEFORE) and after the sale to record the new owner (TRANSFER_AFTER). Must be called outside a
     * transaction, like {@link #lookup(UUID)}. Seizures, annotations and collisions are not reported by Credid: the
     * transfer must also require them from a fresh registry report.
     */
    public RegistryLookupResult refreshForTransfer(UUID vehicleId, RegistryLookupPurpose purpose) {
        if (purpose != RegistryLookupPurpose.TRANSFER_BEFORE && purpose != RegistryLookupPurpose.TRANSFER_AFTER) {
            throw new IllegalArgumentException("Only transfer purposes refresh the registry data");
        }
        return run(vehicleId, null, purpose, true);
    }

    RegistryLookupResult run(UUID vehicleId, UUID requester, RegistryLookupPurpose purpose, boolean force) {
        String provider = registryLookupPort.providerName();
        VehicleRegistryLookupApplier.Prepared prepared = applier.prepare(vehicleId, requester, purpose, force, provider);
        if (!prepared.callsProvider()) {
            return prepared.result();
        }
        String plateClass = CredidPlateClass.of(prepared.plate()).plateClass();
        VehicleRegistryRecord record;
        try {
            record = registryLookupPort.lookup(prepared.plate());
        } catch (VehicleRegistryUnavailableException exception) {
            Log.warnv("Vehicle registry lookup failed for vehicle {0}: {1}", vehicleId, exception.getCode());
            return applier.recordFailure(vehicleId, provider, plateClass, purpose, requester, exception.getCode());
        } catch (RuntimeException exception) {
            Log.warnv("Vehicle registry lookup failed for vehicle {0}: {1}", vehicleId, exception.getClass().getSimpleName());
            return applier.recordFailure(vehicleId, provider, plateClass, purpose, requester,
                    VehicleErrorCodes.VEHICLE_REGISTRY_UNAVAILABLE);
        }
        return applier.apply(vehicleId, provider, plateClass, purpose, requester, record, hashOwner(vehicleId, record));
    }

    /**
     * Keyed hash of the owner identity number. Without the hash key the value is dropped and the case goes to the
     * analyst (OWNER_ID_HASH is required), as in the document flow.
     */
    private String hashOwner(UUID vehicleId, VehicleRegistryRecord record) {
        if (!record.found() || record.ownerIdNumber() == null) {
            return null;
        }
        try {
            return idNumberHasher.hash(record.ownerIdNumber());
        } catch (DependencyUnavailableException exception) {
            Log.warnv("Registry owner of vehicle {0} not hashed: {1}", vehicleId, exception.getCode());
            return null;
        }
    }
}
