package ai.motoria.vehicle.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One call to the vehicle registry by plate: provider, outcome, plate class, why and for whom. Used for the daily quota
 * and as the log of paid queries. Neither the raw answer nor the owner identity is stored here.
 */
@Entity
@Table(name = "registry_lookup", schema = "vehicle")
public class RegistryLookup extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private RegistryLookupPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private RegistryLookupOutcome outcome;

    @Column(name = "error_code", length = 60)
    private String errorCode;

    @Column(name = "plate_class", nullable = false, length = 5)
    private String plateClass;

    @Column(name = "provider_reference", length = 60)
    private String providerReference;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected RegistryLookup() {
    }

    public static RegistryLookup record(UUID vehicleId, String provider, RegistryLookupPurpose purpose,
                                        RegistryLookupOutcome outcome, String errorCode, String plateClass,
                                        String providerReference, UUID requestedBy, Instant checkedAt) {
        RegistryLookup lookup = new RegistryLookup();
        lookup.id = UUID.randomUUID();
        lookup.vehicleId = Objects.requireNonNull(vehicleId);
        lookup.provider = Objects.requireNonNull(provider);
        lookup.purpose = Objects.requireNonNull(purpose);
        lookup.outcome = Objects.requireNonNull(outcome);
        lookup.errorCode = truncate(errorCode, 60);
        lookup.plateClass = Objects.requireNonNull(plateClass);
        lookup.providerReference = truncate(providerReference, 60);
        lookup.requestedBy = requestedBy;
        lookup.checkedAt = Objects.requireNonNull(checkedAt);
        return lookup;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getProvider() {
        return provider;
    }

    public RegistryLookupPurpose getPurpose() {
        return purpose;
    }

    public RegistryLookupOutcome getOutcome() {
        return outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getPlateClass() {
        return plateClass;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
