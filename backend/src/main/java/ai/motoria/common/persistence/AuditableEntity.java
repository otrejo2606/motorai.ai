package ai.motoria.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;

/**
 * Audit columns required on every new table (design D2): created_at, created_by, updated_at, updated_by.
 * Filled from the authenticated subject by the JPA lifecycle callbacks.
 */
@MappedSuperclass
public abstract class AuditableEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    protected String createdBy;

    @Column(name = "updated_at", nullable = false)
    protected Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 255)
    protected String updatedBy;

    @PrePersist
    protected void auditOnCreate() {
        Instant now = Instant.now();
        String subject = AuditSubject.current();
        if (createdAt == null) {
            createdAt = now;
        }
        if (createdBy == null) {
            createdBy = subject;
        }
        updatedAt = now;
        updatedBy = subject;
    }

    @PreUpdate
    protected void auditOnUpdate() {
        updatedAt = Instant.now();
        updatedBy = AuditSubject.current();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
