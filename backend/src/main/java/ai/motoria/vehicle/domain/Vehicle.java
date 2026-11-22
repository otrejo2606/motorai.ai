package ai.motoria.vehicle.domain;

import ai.motoria.common.persistence.AuditableEntity;
import ai.motoria.common.port.ExtractedEncumbrance;
import ai.motoria.common.port.ExtractedValue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Vehicle identified by country and normalized plate (design D3). State machine:
 * REGISTRY_PENDING, then REGISTRY_IN_REVIEW when a registry document is attached, then REGISTRY_VERIFIED or
 * REGISTRY_REJECTED. A rejected or stale verified vehicle can receive a new document.
 */
@Entity
@Table(name = "vehicle", schema = "vehicle")
public class Vehicle extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "plate", nullable = false, length = 20)
    private String plate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private VehicleStatus status;

    @Column(name = "transfer_blocked", nullable = false)
    private boolean transferBlocked;

    @Column(name = "transfer_block_reason", length = 500)
    private String transferBlockReason;

    @Column(name = "registry_checked_at")
    private Instant registryCheckedAt;

    @Column(name = "registry_document_media_id")
    private UUID registryDocumentMediaId;

    @Column(name = "registry_document_date")
    private LocalDate registryDocumentDate;

    @Column(name = "extraction_inference_id")
    private UUID extractionInferenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_reason", length = 40)
    private ReviewReason reviewReason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleAttribute> attributes = new ArrayList<>();

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RegistryEncumbrance> encumbrances = new ArrayList<>();

    protected Vehicle() {
    }

    public static Vehicle register(String countryCode, String normalizedPlate, UUID requestedBy) {
        Vehicle vehicle = new Vehicle();
        vehicle.id = UUID.randomUUID();
        vehicle.countryCode = Objects.requireNonNull(countryCode);
        vehicle.plate = Objects.requireNonNull(normalizedPlate);
        vehicle.requestedBy = Objects.requireNonNull(requestedBy);
        vehicle.status = VehicleStatus.REGISTRY_PENDING;
        return vehicle;
    }

    public boolean isRegistryStale(Instant now, Duration maxAge) {
        return registryCheckedAt == null || registryCheckedAt.plus(maxAge).isBefore(now);
    }

    public boolean canReceiveRegistryDocument(Instant now, Duration maxAge) {
        return status == VehicleStatus.REGISTRY_PENDING
                || status == VehicleStatus.REGISTRY_REJECTED
                || (status == VehicleStatus.REGISTRY_VERIFIED && isRegistryStale(now, maxAge));
    }

    public void attachRegistryDocument(UUID documentMediaId, UUID requester, Instant now, Duration maxAge) {
        if (!canReceiveRegistryDocument(now, maxAge)) {
            throw new IllegalStateException("Vehicle cannot receive a registry document in state " + status);
        }
        this.registryDocumentMediaId = Objects.requireNonNull(documentMediaId);
        this.requestedBy = Objects.requireNonNull(requester);
        this.status = VehicleStatus.REGISTRY_IN_REVIEW;
        this.reviewReason = null;
        this.rejectionReason = null;
        this.extractionInferenceId = null;
    }

    public boolean isAwaitingExtraction(UUID documentMediaId) {
        return status == VehicleStatus.REGISTRY_IN_REVIEW
                && documentMediaId.equals(registryDocumentMediaId)
                && reviewReason == null;
    }

    /**
     * Stores the values read from the registry document (in place for existing keys to respect the unique
     * constraint) and the encumbrances, and records why the case goes to the analyst.
     */
    public void applyExtraction(UUID inferenceId, List<ExtractedValue> values, List<ExtractedEncumbrance> extractedEncumbrances,
                                LocalDate documentDate, ReviewReason reason, Instant now) {
        ensureStatus(VehicleStatus.REGISTRY_IN_REVIEW);
        Set<VehicleAttributeKey> received = new HashSet<>();
        for (ExtractedValue value : values) {
            Optional<VehicleAttributeKey> key = parseKey(value.key());
            if (key.isEmpty() || !received.add(key.get())) {
                continue;
            }
            Optional<VehicleAttribute> existing = findAttribute(key.get());
            if (existing.isPresent()) {
                existing.get().replace(value.value(), AttributeSource.REGISTRY_DOCUMENT, registryDocumentMediaId,
                        value.page(), value.confidence(), now);
            } else {
                attributes.add(VehicleAttribute.create(this, key.get(), value.value(), AttributeSource.REGISTRY_DOCUMENT,
                        registryDocumentMediaId, value.page(), value.confidence(), now));
            }
        }
        attributes.removeIf(attribute -> attribute.getSource().isOfficial() && !received.contains(attribute.getKey()));
        encumbrances.clear();
        for (ExtractedEncumbrance encumbrance : extractedEncumbrances) {
            encumbrances.add(RegistryEncumbrance.create(this, parseEncumbranceType(encumbrance.type()),
                    encumbrance.description(), registryDocumentMediaId, encumbrance.page(), encumbrance.confidence()));
        }
        this.registryDocumentDate = documentDate;
        this.extractionInferenceId = inferenceId;
        this.reviewReason = Objects.requireNonNull(reason);
    }

    /**
     * The extraction failed or the plate did not match: no value is applied and the case goes to the analyst.
     */
    public void requireReviewWithoutData(UUID inferenceId, ReviewReason reason) {
        ensureStatus(VehicleStatus.REGISTRY_IN_REVIEW);
        this.extractionInferenceId = inferenceId;
        this.reviewReason = Objects.requireNonNull(reason);
    }

    public void correctAttribute(VehicleAttributeKey key, String value, String analyst, Instant now) {
        ensureStatus(VehicleStatus.REGISTRY_IN_REVIEW);
        Optional<VehicleAttribute> existing = findAttribute(key);
        if (existing.isPresent()) {
            existing.get().correct(value, analyst, now);
        } else {
            VehicleAttribute created = VehicleAttribute.create(this, key, value, AttributeSource.ANALYST,
                    registryDocumentMediaId, null, null, now);
            created.correct(value, analyst, now);
            attributes.add(created);
        }
    }

    public List<VehicleAttributeKey> missingAttributes(Collection<VehicleAttributeKey> required) {
        return required.stream()
                .filter(key -> findAttribute(key).isEmpty())
                .toList();
    }

    public boolean hasAnalystCorrections() {
        return attributes.stream().anyMatch(attribute -> attribute.getSource() == AttributeSource.ANALYST);
    }

    /**
     * Confirms all values: the vehicle becomes REGISTRY_VERIFIED and the transfer blocks are computed.
     */
    public void approve(Instant now) {
        ensureStatus(VehicleStatus.REGISTRY_IN_REVIEW);
        this.status = VehicleStatus.REGISTRY_VERIFIED;
        this.registryCheckedAt = registryDocumentDate != null
                ? registryDocumentDate.atStartOfDay().toInstant(ZoneOffset.UTC)
                : now;
        this.transferBlocked = TransferBlockPolicy.isBlocked(encumbrances);
        this.transferBlockReason = TransferBlockPolicy.reason(encumbrances);
    }

    public void reject(String reason) {
        ensureStatus(VehicleStatus.REGISTRY_IN_REVIEW);
        this.status = VehicleStatus.REGISTRY_REJECTED;
        this.rejectionReason = reason;
    }

    /**
     * "Que Motoria lo obtenga": the vehicle waits in REGISTRY_PENDING until the operator uploads the report.
     */
    public void awaitRegistryRequest() {
        if (status == VehicleStatus.REGISTRY_REJECTED) {
            this.status = VehicleStatus.REGISTRY_PENDING;
            return;
        }
        ensureStatus(VehicleStatus.REGISTRY_PENDING);
    }

    public Optional<VehicleAttribute> findAttribute(VehicleAttributeKey key) {
        return attributes.stream().filter(attribute -> attribute.getKey() == key).findFirst();
    }

    public Optional<String> attributeValue(VehicleAttributeKey key) {
        return findAttribute(key).map(VehicleAttribute::getValue);
    }

    private static Optional<VehicleAttributeKey> parseKey(String key) {
        try {
            return Optional.of(VehicleAttributeKey.valueOf(key));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private static EncumbranceType parseEncumbranceType(String type) {
        try {
            return EncumbranceType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return EncumbranceType.OTHER;
        }
    }

    private void ensureStatus(VehicleStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Vehicle must be " + expected + " but was " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPlate() {
        return plate;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public boolean isTransferBlocked() {
        return transferBlocked;
    }

    public String getTransferBlockReason() {
        return transferBlockReason;
    }

    public Instant getRegistryCheckedAt() {
        return registryCheckedAt;
    }

    public UUID getRegistryDocumentMediaId() {
        return registryDocumentMediaId;
    }

    public LocalDate getRegistryDocumentDate() {
        return registryDocumentDate;
    }

    public UUID getExtractionInferenceId() {
        return extractionInferenceId;
    }

    public ReviewReason getReviewReason() {
        return reviewReason;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public List<VehicleAttribute> getAttributes() {
        return attributes;
    }

    public List<RegistryEncumbrance> getEncumbrances() {
        return encumbrances;
    }
}
