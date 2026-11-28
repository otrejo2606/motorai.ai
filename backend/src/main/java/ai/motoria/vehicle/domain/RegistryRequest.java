package ai.motoria.vehicle.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * "Que Motoria lo obtenga": task for a backoffice operator to buy and upload the registry report.
 */
@Entity
@Table(name = "registry_request", schema = "vehicle")
public class RegistryRequest extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "accepted_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal acceptedFee;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegistryRequestStatus status;

    @Column(name = "document_media_id")
    private UUID documentMediaId;

    @Column(name = "fulfilled_by", length = 255)
    private String fulfilledBy;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    protected RegistryRequest() {
    }

    public static RegistryRequest open(UUID vehicleId, UUID requestedBy, BigDecimal acceptedFee, String currency) {
        RegistryRequest request = new RegistryRequest();
        request.id = UUID.randomUUID();
        request.vehicleId = Objects.requireNonNull(vehicleId);
        request.requestedBy = Objects.requireNonNull(requestedBy);
        request.acceptedFee = Objects.requireNonNull(acceptedFee);
        request.currency = Objects.requireNonNull(currency);
        request.status = RegistryRequestStatus.PENDING;
        return request;
    }

    public void fulfill(UUID documentMediaId, String operator, Instant now) {
        if (status != RegistryRequestStatus.PENDING) {
            throw new IllegalStateException("Registry request must be PENDING but was " + status);
        }
        this.documentMediaId = Objects.requireNonNull(documentMediaId);
        this.fulfilledBy = Objects.requireNonNull(operator);
        this.fulfilledAt = now;
        this.status = RegistryRequestStatus.FULFILLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public BigDecimal getAcceptedFee() {
        return acceptedFee;
    }

    public String getCurrency() {
        return currency;
    }

    public RegistryRequestStatus getStatus() {
        return status;
    }

    public UUID getDocumentMediaId() {
        return documentMediaId;
    }

    public String getFulfilledBy() {
        return fulfilledBy;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }
}
