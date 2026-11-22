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
 * One consent decision (grant or revocation). Rows are never updated: the latest decision per type is the current state.
 */
@Entity
@Table(name = "consent", schema = "usr")
public class Consent extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(name = "text_version", nullable = false, length = 20)
    private String textVersion;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected Consent() {
    }

    public static Consent decide(UUID userId, ConsentType type, String textVersion, boolean granted, Instant decidedAt, String ipAddress) {
        Consent consent = new Consent();
        consent.id = UUID.randomUUID();
        consent.userId = Objects.requireNonNull(userId);
        consent.consentType = Objects.requireNonNull(type);
        consent.textVersion = Objects.requireNonNull(textVersion);
        consent.granted = granted;
        consent.decidedAt = Objects.requireNonNull(decidedAt);
        consent.ipAddress = ipAddress;
        return consent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ConsentType getConsentType() {
        return consentType;
    }

    public String getTextVersion() {
        return textVersion;
    }

    public boolean isGranted() {
        return granted;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
