package ai.motoria.listing.domain;

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
 * Photo of a protocol slot. {@code publicUrl} points to the metadata-free public copy; the original stays private.
 * Since V10 it also keeps the angle check of the local classifier, what the photo reads (VIN, odometer), the reading
 * anomaly and the decision of the analyst (add-captura-guiada-fotos). Neither the image nor the raw answer of the
 * classifier is stored.
 */
@Entity
@Table(name = "listing_photo", schema = "listing")
public class ListingPhoto extends AuditableEntity {

    private static final int MAX_READ_VIN = 30;
    private static final int MAX_REASON = 60;
    private static final int MAX_MODEL_VERSION = 60;
    private static final int MAX_SLOT = 30;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "slot", nullable = false, length = 30)
    private String slot;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "public_url", nullable = false, length = 1000)
    private String publicUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "angle_status", nullable = false, length = 20)
    private PhotoAngleStatus angleStatus = PhotoAngleStatus.NOT_CHECKED;

    @Column(name = "detected_slot", length = 30)
    private String detectedSlot;

    @Column(name = "angle_confidence", precision = 5, scale = 4)
    private BigDecimal angleConfidence;

    /**
     * PhotoRejectionReason of a MISMATCH, or the PHOTO_CLASSIFIER_* code of an UNAVAILABLE check.
     */
    @Column(name = "angle_reason", length = 60)
    private String angleReason;

    @Column(name = "angle_model_version", length = 60)
    private String angleModelVersion;

    @Column(name = "angle_latency_ms")
    private Long angleLatencyMs;

    @Column(name = "angle_checked_at")
    private Instant angleCheckedAt;

    @Column(name = "read_vin", length = 30)
    private String readVin;

    @Column(name = "read_odometer_km")
    private Integer readOdometerKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_anomaly", length = 30)
    private PhotoAnomaly readingAnomaly;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_decision", length = 20)
    private PhotoReviewDecision reviewDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_reason", length = 30)
    private PhotoRejectionReason reviewReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ListingPhoto() {
    }

    static ListingPhoto create(Listing listing, String slot, UUID mediaId, String publicUrl) {
        ListingPhoto photo = new ListingPhoto();
        photo.id = UUID.randomUUID();
        photo.listing = Objects.requireNonNull(listing);
        photo.slot = Objects.requireNonNull(slot);
        photo.replace(mediaId, publicUrl);
        return photo;
    }

    /**
     * A new (or the same, sent again) photo always starts a new angle check: the previous result, readings and
     * decision of the analyst no longer apply.
     */
    void replace(UUID mediaId, String publicUrl) {
        this.mediaId = Objects.requireNonNull(mediaId);
        this.publicUrl = Objects.requireNonNull(publicUrl);
        this.angleStatus = PhotoAngleStatus.PENDING;
        this.detectedSlot = null;
        this.angleConfidence = null;
        this.angleReason = null;
        this.angleModelVersion = null;
        this.angleLatencyMs = null;
        this.angleCheckedAt = null;
        this.readVin = null;
        this.readOdometerKm = null;
        this.readingAnomaly = null;
        this.reviewDecision = null;
        this.reviewReason = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
    }

    /**
     * Stores the result of the angle check only if it is for the photo that is still in the slot and the check is
     * still pending (a newer photo, or a second answer, is ignored).
     *
     * @return true when the result was stored
     */
    public boolean applyAngleCheck(UUID checkedMediaId, PhotoAngleStatus status, String detectedSlot, Double confidence,
                                   String modelVersion, Long latencyMs, String reason, String vin, Integer odometerKm,
                                   Instant now) {
        if (!mediaId.equals(checkedMediaId) || angleStatus != PhotoAngleStatus.PENDING
                || status == null || status == PhotoAngleStatus.PENDING) {
            return false;
        }
        this.angleStatus = status;
        this.detectedSlot = truncate(detectedSlot, MAX_SLOT);
        this.angleConfidence = confidence == null || confidence.isNaN()
                ? null
                : BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, confidence))).setScale(4, RoundingMode.HALF_UP);
        this.angleReason = truncate(reason, MAX_REASON);
        this.angleModelVersion = truncate(modelVersion, MAX_MODEL_VERSION);
        this.angleLatencyMs = latencyMs;
        this.angleCheckedAt = status == PhotoAngleStatus.NOT_CHECKED ? null : now;
        this.readVin = truncate(vin, MAX_READ_VIN);
        this.readOdometerKm = odometerKm != null && odometerKm >= 0 ? odometerKm : null;
        return true;
    }

    /**
     * @return true only when the anomaly is new (so the seller is notified once per photo)
     */
    public boolean recordReadingAnomaly(PhotoAnomaly anomaly) {
        if (Objects.equals(this.readingAnomaly, anomaly)) {
            return false;
        }
        this.readingAnomaly = anomaly;
        return anomaly != null;
    }

    /**
     * The analyst decides on photos that were not a clear MATCH or that read something that does not fit.
     */
    public boolean isReviewable() {
        if (reviewDecision != null) {
            return false;
        }
        return angleStatus == PhotoAngleStatus.UNCERTAIN
                || angleStatus == PhotoAngleStatus.UNAVAILABLE
                || angleStatus == PhotoAngleStatus.MISMATCH
                || readingAnomaly != null;
    }

    /**
     * In the analyst tray: UNCERTAIN, UNAVAILABLE or with a reading anomaly, without a decision yet. A MISMATCH is for
     * the seller to repeat, not for the tray.
     */
    public boolean awaitsAnalyst() {
        return reviewDecision == null
                && (angleStatus == PhotoAngleStatus.UNCERTAIN
                || angleStatus == PhotoAngleStatus.UNAVAILABLE
                || readingAnomaly != null);
    }

    public void review(PhotoReviewDecision decision, PhotoRejectionReason reason, UUID reviewer, Instant now) {
        this.reviewDecision = Objects.requireNonNull(decision);
        this.reviewReason = decision == PhotoReviewDecision.REJECTED ? Objects.requireNonNull(reason) : null;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
    }

    /**
     * A MISMATCH not accepted by the analyst, or a photo rejected by the analyst, must be repeated before the review.
     */
    public boolean mustBeRepeated() {
        if (reviewDecision == PhotoReviewDecision.REJECTED) {
            return true;
        }
        return angleStatus == PhotoAngleStatus.MISMATCH && reviewDecision != PhotoReviewDecision.ACCEPTED;
    }

    /**
     * Translatable reason to show when the photo must be repeated: the analyst's reason, otherwise the classifier's.
     */
    public String repeatReason() {
        if (reviewDecision == PhotoReviewDecision.REJECTED && reviewReason != null) {
            return reviewReason.name();
        }
        return mustBeRepeated() ? angleReason : null;
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

    public Listing getListing() {
        return listing;
    }

    public String getSlot() {
        return slot;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public PhotoAngleStatus getAngleStatus() {
        return angleStatus;
    }

    public String getDetectedSlot() {
        return detectedSlot;
    }

    public BigDecimal getAngleConfidence() {
        return angleConfidence;
    }

    public String getAngleReason() {
        return angleReason;
    }

    public String getAngleModelVersion() {
        return angleModelVersion;
    }

    public Long getAngleLatencyMs() {
        return angleLatencyMs;
    }

    public Instant getAngleCheckedAt() {
        return angleCheckedAt;
    }

    public String getReadVin() {
        return readVin;
    }

    public Integer getReadOdometerKm() {
        return readOdometerKm;
    }

    public PhotoAnomaly getReadingAnomaly() {
        return readingAnomaly;
    }

    public PhotoReviewDecision getReviewDecision() {
        return reviewDecision;
    }

    public PhotoRejectionReason getReviewReason() {
        return reviewReason;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
