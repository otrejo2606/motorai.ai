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
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One registry value with its source, evidence (media and page), confidence and date (design D3).
 * When an analyst corrects a value read by the AI, the AI value is kept in {@code aiValue}.
 */
@Entity
@Table(name = "vehicle_attribute", schema = "vehicle")
public class VehicleAttribute extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Enumerated(EnumType.STRING)
    @Column(name = "attr_key", nullable = false, length = 30)
    private VehicleAttributeKey key;

    @Column(name = "attr_value", nullable = false, length = 500)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private AttributeSource source;

    @Column(name = "evidence_media_id")
    private UUID evidenceMediaId;

    @Column(name = "evidence_page")
    private Integer evidencePage;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "ai_value", length = 500)
    private String aiValue;

    @Column(name = "corrected_by", length = 255)
    private String correctedBy;

    protected VehicleAttribute() {
    }

    static VehicleAttribute create(Vehicle vehicle, VehicleAttributeKey key, String value, AttributeSource source,
                                   UUID evidenceMediaId, Integer evidencePage, Double confidence, Instant observedAt) {
        VehicleAttribute attribute = new VehicleAttribute();
        attribute.id = UUID.randomUUID();
        attribute.vehicle = Objects.requireNonNull(vehicle);
        attribute.key = Objects.requireNonNull(key);
        attribute.replace(value, source, evidenceMediaId, evidencePage, confidence, observedAt);
        return attribute;
    }

    void replace(String value, AttributeSource source, UUID evidenceMediaId, Integer evidencePage,
                 Double confidence, Instant observedAt) {
        this.value = Objects.requireNonNull(value);
        this.source = Objects.requireNonNull(source);
        this.evidenceMediaId = evidenceMediaId;
        this.evidencePage = evidencePage;
        this.confidence = confidence == null ? null : BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP);
        this.observedAt = Objects.requireNonNull(observedAt);
        this.aiValue = null;
        this.correctedBy = null;
    }

    void correct(String newValue, String analyst, Instant now) {
        if (source == AttributeSource.REGISTRY_DOCUMENT && aiValue == null) {
            this.aiValue = this.value;
        }
        this.value = Objects.requireNonNull(newValue);
        this.source = AttributeSource.ANALYST;
        this.correctedBy = analyst;
        this.confidence = null;
        this.observedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public VehicleAttributeKey getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public AttributeSource getSource() {
        return source;
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

    public Instant getObservedAt() {
        return observedAt;
    }

    public String getAiValue() {
        return aiValue;
    }

    public String getCorrectedBy() {
        return correctedBy;
    }
}
