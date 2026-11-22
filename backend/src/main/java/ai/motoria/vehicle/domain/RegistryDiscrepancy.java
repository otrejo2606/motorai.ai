package ai.motoria.vehicle.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Difference between a registry value and a value declared by the seller (base requirement
 * "Dato verificado frente a dato declarado"). The registry value is always kept.
 */
@Entity
@Table(name = "registry_discrepancy", schema = "vehicle")
public class RegistryDiscrepancy extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "listing_id")
    private UUID listingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attr_key", nullable = false, length = 30)
    private VehicleAttributeKey key;

    @Column(name = "registry_value", length = 500)
    private String registryValue;

    @Column(name = "declared_value", length = 500)
    private String declaredValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DiscrepancyStatus status;

    protected RegistryDiscrepancy() {
    }

    public static RegistryDiscrepancy open(UUID vehicleId, UUID listingId, VehicleAttributeKey key,
                                           String registryValue, String declaredValue) {
        RegistryDiscrepancy discrepancy = new RegistryDiscrepancy();
        discrepancy.id = UUID.randomUUID();
        discrepancy.vehicleId = Objects.requireNonNull(vehicleId);
        discrepancy.listingId = listingId;
        discrepancy.key = Objects.requireNonNull(key);
        discrepancy.registryValue = registryValue;
        discrepancy.declaredValue = declaredValue;
        discrepancy.status = DiscrepancyStatus.OPEN;
        return discrepancy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getListingId() {
        return listingId;
    }

    public VehicleAttributeKey getKey() {
        return key;
    }

    public String getRegistryValue() {
        return registryValue;
    }

    public String getDeclaredValue() {
        return declaredValue;
    }

    public DiscrepancyStatus getStatus() {
        return status;
    }
}
