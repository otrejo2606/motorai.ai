package ai.motoria.media.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Audit trail of private reads (read URLs and server-side content reads).
 */
@Entity
@Table(name = "media_access_log", schema = "media")
public class MediaAccessLog extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "accessed_by", nullable = false, length = 255)
    private String accessedBy;

    @Column(name = "purpose", nullable = false, length = 60)
    private String purpose;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    protected MediaAccessLog() {
    }

    public static MediaAccessLog record(UUID mediaId, String accessedBy, String purpose, Instant accessedAt) {
        MediaAccessLog log = new MediaAccessLog();
        log.id = UUID.randomUUID();
        log.mediaId = Objects.requireNonNull(mediaId);
        log.accessedBy = Objects.requireNonNull(accessedBy);
        log.purpose = purpose == null || purpose.length() <= 60 ? purpose : purpose.substring(0, 60);
        log.accessedAt = Objects.requireNonNull(accessedAt);
        return log;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public String getAccessedBy() {
        return accessedBy;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }
}
