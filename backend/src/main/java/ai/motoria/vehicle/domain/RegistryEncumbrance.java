package ai.motoria.vehicle.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "registry_encumbrance", schema = "vehicle")
public class RegistryEncumbrance extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "encumbrance_type", nullable = false, length = 20)
    private EncumbranceType type;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "blocks_transfer", nullable = false)
    private boolean blocksTransfer;

    @Column(name = "evidence_media_id")
    private UUID evidenceMediaId;

    @Column(name = "evidence_page")
    private Integer evidencePage;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    protected RegistryEncumbrance() {
    }

    static RegistryEncumbrance create(Vehicle vehicle, EncumbranceType type, String description,
                                      UUID evidenceMediaId, Integer evidencePage, Double confidence) {
        RegistryEncumbrance encumbrance = new RegistryEncumbrance();
        encumbrance.id = UUID.randomUUID();
        encumbrance.vehicle = Objects.requireNonNull(vehicle);
        encumbrance.type = Objects.requireNonNull(type);
        encumbrance.description = description;
        encumbrance.blocksTransfer = TransferBlockPolicy.blocksTransfer(type);
        encumbrance.evidenceMediaId = evidenceMediaId;
        encumbrance.evidencePage = evidencePage;
        encumbrance.confidence = confidence == null ? null : BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP);
        return encumbrance;
    }

    public UUID getId() {
        return id;
    }

    public EncumbranceType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isBlocksTransfer() {
        return blocksTransfer;
    }

    public UUID getEvidenceMediaId() {
        return evidenceMediaId;
    }

    public Integer getEvidencePage() {
        return evidencePage;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }
}
