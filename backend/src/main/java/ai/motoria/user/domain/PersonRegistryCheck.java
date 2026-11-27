package ai.motoria.user.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One paid query to the person registry, indexed by the keyed hash of the identity number (never in clear).
 * Reused while it is fresh (motoria.user.person-registry.cache-ttl) so the same person is not paid twice.
 */
@Entity
@Table(name = "person_registry_check", schema = "usr")
public class PersonRegistryCheck extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "id_number_hash", nullable = false, length = 64)
    private String idNumberHash;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "found", nullable = false)
    private boolean found;

    @Column(name = "person_type", length = 1)
    private String personType;

    @Column(name = "full_name", length = 300)
    private String fullName;

    @Column(name = "document_expires_on")
    private LocalDate documentExpiresOn;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "provider_reference", length = 60)
    private String providerReference;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected PersonRegistryCheck() {
    }

    public static PersonRegistryCheck record(String idNumberHash, String provider, boolean found, String personType,
                                             String fullName, LocalDate documentExpiresOn, String nationality,
                                             String providerReference, Instant checkedAt) {
        PersonRegistryCheck check = new PersonRegistryCheck();
        check.id = UUID.randomUUID();
        check.idNumberHash = Objects.requireNonNull(idNumberHash);
        check.provider = Objects.requireNonNull(provider);
        check.found = found;
        check.personType = personType;
        check.fullName = truncate(fullName, 300);
        check.documentExpiresOn = documentExpiresOn;
        check.nationality = truncate(nationality, 100);
        check.providerReference = truncate(providerReference, 60);
        check.checkedAt = Objects.requireNonNull(checkedAt);
        return check;
    }

    public boolean isDocumentExpired(LocalDate today) {
        return documentExpiresOn != null && documentExpiresOn.isBefore(today);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public String getIdNumberHash() {
        return idNumberHash;
    }

    public String getProvider() {
        return provider;
    }

    public boolean isFound() {
        return found;
    }

    public String getPersonType() {
        return personType;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDocumentExpiresOn() {
        return documentExpiresOn;
    }

    public String getNationality() {
        return nationality;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }
}
