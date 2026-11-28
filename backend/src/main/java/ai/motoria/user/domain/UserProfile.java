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
 * Local profile of a Keycloak user (id = sub). The identity number and its type are stored encrypted;
 * {@code idNumberHash} is the keyed hash used to compare with the registry owner.
 */
@Entity
@Table(name = "user_profile", schema = "usr")
public class UserProfile extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "id_type_enc", length = 200)
    private String idTypeEnc;

    @Column(name = "id_number_enc", length = 300)
    private String idNumberEnc;

    @Column(name = "id_number_hash", length = 64)
    private String idNumberHash;

    @Column(name = "id_number_last4", length = 4)
    private String idNumberLast4;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_level", nullable = false, length = 10)
    private IdentityLevel identityLevel;

    @Column(name = "verification_provider", length = 40)
    private String verificationProvider;

    @Column(name = "verification_reference", length = 120)
    private String verificationReference;

    protected UserProfile() {
    }

    public static UserProfile create(UUID id, String email, String countryCode) {
        UserProfile profile = new UserProfile();
        profile.id = Objects.requireNonNull(id);
        profile.email = email;
        profile.countryCode = countryCode;
        profile.identityLevel = IdentityLevel.LEVEL_1;
        return profile;
    }

    public void updateContact(String fullName, String phone, String countryCode) {
        this.fullName = fullName;
        this.phone = phone;
        this.countryCode = countryCode;
    }

    /**
     * Once the identity is verified (LEVEL_2 or more) the identity number can no longer change.
     */
    public boolean isIdentityNumberLocked() {
        return identityLevel.rank() >= IdentityLevel.LEVEL_2.rank() && idNumberHash != null;
    }

    public void updateIdentityNumber(String idTypeEnc, String idNumberEnc, String idNumberHash, String idNumberLast4) {
        if (isIdentityNumberLocked() && !Objects.equals(this.idNumberHash, idNumberHash)) {
            throw new IllegalStateException("The verified identity number cannot change");
        }
        this.idTypeEnc = idTypeEnc;
        this.idNumberEnc = idNumberEnc;
        this.idNumberHash = idNumberHash;
        this.idNumberLast4 = idNumberLast4;
    }

    public boolean hasIdentityNumber() {
        return idNumberHash != null;
    }

    /**
     * Raises the identity level; it never lowers it.
     */
    public void raiseIdentityLevel(IdentityLevel level, String provider, String reference) {
        if (level.rank() > identityLevel.rank()) {
            this.identityLevel = level;
        }
        this.verificationProvider = provider;
        this.verificationReference = reference;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getIdTypeEnc() {
        return idTypeEnc;
    }

    public String getIdNumberEnc() {
        return idNumberEnc;
    }

    public String getIdNumberHash() {
        return idNumberHash;
    }

    public String getIdNumberLast4() {
        return idNumberLast4;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public IdentityLevel getIdentityLevel() {
        return identityLevel;
    }

    public String getVerificationProvider() {
        return verificationProvider;
    }

    public String getVerificationReference() {
        return verificationReference;
    }
}
