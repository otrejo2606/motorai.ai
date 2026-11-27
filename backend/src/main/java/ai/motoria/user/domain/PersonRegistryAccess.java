package ai.motoria.user.domain;

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
 * Log of each use of a person registry check, paid or reused: who (created_by), what for and why.
 */
@Entity
@Table(name = "person_registry_access", schema = "usr")
public class PersonRegistryAccess extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "check_id", nullable = false)
    private UUID checkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private PersonRegistryPurpose purpose;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "verification_id")
    private UUID verificationId;

    protected PersonRegistryAccess() {
    }

    public static PersonRegistryAccess log(UUID checkId, PersonRegistryPurpose purpose, String reason, UUID verificationId) {
        PersonRegistryAccess access = new PersonRegistryAccess();
        access.id = UUID.randomUUID();
        access.checkId = Objects.requireNonNull(checkId);
        access.purpose = Objects.requireNonNull(purpose);
        access.reason = reason;
        access.verificationId = verificationId;
        return access;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCheckId() {
        return checkId;
    }

    public PersonRegistryPurpose getPurpose() {
        return purpose;
    }

    public String getReason() {
        return reason;
    }

    public UUID getVerificationId() {
        return verificationId;
    }
}
