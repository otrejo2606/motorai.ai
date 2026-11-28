package ai.motoria.user.domain;

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
 * Identity verification case. Manual flow (design D1): CHALLENGE_ISSUED, then SUBMITTED with the document
 * and selfie media, then APPROVED or REJECTED by a BACKOFFICE analyst.
 */
@Entity
@Table(name = "identity_verification", schema = "usr")
public class IdentityVerification extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdentityVerificationStatus status;

    @Column(name = "challenge_code", nullable = false, length = 12)
    private String challengeCode;

    @Column(name = "challenge_expires_at", nullable = false)
    private Instant challengeExpiresAt;

    @Column(name = "document_front_media_id")
    private UUID documentFrontMediaId;

    @Column(name = "document_back_media_id")
    private UUID documentBackMediaId;

    @Column(name = "selfie_media_id")
    private UUID selfieMediaId;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "registry_status", length = 20)
    private RegistryMatchStatus registryStatus;

    @Column(name = "registry_check_id")
    private UUID registryCheckId;

    protected IdentityVerification() {
    }

    public static IdentityVerification issue(UUID userId, String provider, String challengeCode, Instant challengeExpiresAt) {
        IdentityVerification verification = new IdentityVerification();
        verification.id = UUID.randomUUID();
        verification.userId = Objects.requireNonNull(userId);
        verification.provider = Objects.requireNonNull(provider);
        verification.challengeCode = Objects.requireNonNull(challengeCode);
        verification.challengeExpiresAt = Objects.requireNonNull(challengeExpiresAt);
        verification.status = IdentityVerificationStatus.CHALLENGE_ISSUED;
        return verification;
    }

    public boolean isChallengeExpired(Instant now) {
        return now.isAfter(challengeExpiresAt);
    }

    public void submit(UUID documentFrontMediaId, UUID documentBackMediaId, UUID selfieMediaId, Instant now) {
        ensureStatus(IdentityVerificationStatus.CHALLENGE_ISSUED);
        if (isChallengeExpired(now)) {
            throw new IllegalStateException("The challenge code expired");
        }
        this.documentFrontMediaId = Objects.requireNonNull(documentFrontMediaId);
        this.documentBackMediaId = Objects.requireNonNull(documentBackMediaId);
        this.selfieMediaId = Objects.requireNonNull(selfieMediaId);
        this.submittedAt = now;
        this.status = IdentityVerificationStatus.SUBMITTED;
    }

    public void approve(String reviewer, Instant now) {
        ensureStatus(IdentityVerificationStatus.SUBMITTED);
        this.status = IdentityVerificationStatus.APPROVED;
        this.reviewedBy = Objects.requireNonNull(reviewer);
        this.reviewedAt = now;
    }

    public void reject(String reviewer, String reason, Instant now) {
        ensureStatus(IdentityVerificationStatus.SUBMITTED);
        this.status = IdentityVerificationStatus.REJECTED;
        this.reviewedBy = Objects.requireNonNull(reviewer);
        this.reviewedAt = now;
        this.rejectionReason = reason;
    }

    /**
     * Stores the comparison with the person registry. Only while the case waits for the analyst.
     *
     * @param checkId the registry check used, or null when the registry could not be queried
     */
    public void recordRegistryCheck(RegistryMatchStatus status, UUID checkId) {
        ensureStatus(IdentityVerificationStatus.SUBMITTED);
        this.registryStatus = Objects.requireNonNull(status);
        this.registryCheckId = checkId;
    }

    /**
     * Closes a challenge that was replaced by a new one.
     */
    public void expire() {
        if (status == IdentityVerificationStatus.CHALLENGE_ISSUED) {
            this.status = IdentityVerificationStatus.EXPIRED;
        }
    }

    private void ensureStatus(IdentityVerificationStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Identity verification must be " + expected + " but was " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public IdentityVerificationStatus getStatus() {
        return status;
    }

    public String getChallengeCode() {
        return challengeCode;
    }

    public Instant getChallengeExpiresAt() {
        return challengeExpiresAt;
    }

    public UUID getDocumentFrontMediaId() {
        return documentFrontMediaId;
    }

    public UUID getDocumentBackMediaId() {
        return documentBackMediaId;
    }

    public UUID getSelfieMediaId() {
        return selfieMediaId;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public RegistryMatchStatus getRegistryStatus() {
        return registryStatus;
    }

    public UUID getRegistryCheckId() {
        return registryCheckId;
    }
}
