package ai.motoria.user.mapper;

import ai.motoria.user.domain.Consent;
import ai.motoria.user.domain.ConsentType;
import ai.motoria.user.domain.IdType;
import ai.motoria.user.domain.IdentityVerification;
import ai.motoria.user.domain.PersonRegistryCheck;
import ai.motoria.user.domain.UserProfile;
import ai.motoria.user.dto.BackofficeIdentityVerificationResponse;
import ai.motoria.user.dto.ConsentResponse;
import ai.motoria.user.dto.IdentityChallengeResponse;
import ai.motoria.user.dto.IdentityVerificationResponse;
import ai.motoria.user.dto.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface UserMapper {

    /**
     * @param idType decrypted identity type (the entity only holds it encrypted)
     */
    @Mapping(target = "id", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.fullName")
    @Mapping(target = "idType", source = "idType")
    @Mapping(target = "idNumberLast4", source = "profile.idNumberLast4")
    @Mapping(target = "phone", source = "profile.phone")
    @Mapping(target = "email", source = "profile.email")
    @Mapping(target = "countryCode", source = "profile.countryCode")
    @Mapping(target = "identityLevel", source = "profile.identityLevel")
    UserProfileResponse toResponse(UserProfile profile, IdType idType);

    default ConsentResponse toResponse(ConsentType type, String currentTextVersion, Consent latest) {
        return new ConsentResponse(
                type,
                currentTextVersion,
                latest != null ? latest.getTextVersion() : null,
                latest != null && latest.isGranted(),
                latest != null ? latest.getDecidedAt() : null);
    }

    @Mapping(target = "expiresAt", source = "challengeExpiresAt")
    IdentityVerificationResponse toResponse(IdentityVerification verification);

    @Mapping(target = "expiresAt", source = "challengeExpiresAt")
    IdentityChallengeResponse toChallengeResponse(IdentityVerification verification);

    /**
     * @param check registry check recorded on the verification, or null
     */
    default BackofficeIdentityVerificationResponse toBackofficeResponse(
            IdentityVerification verification, UserProfile profile, IdType idType, PersonRegistryCheck check) {
        return new BackofficeIdentityVerificationResponse(
                verification.getId(),
                verification.getUserId(),
                profile != null ? profile.getFullName() : null,
                idType,
                profile != null ? profile.getIdNumberLast4() : null,
                verification.getStatus(),
                verification.getChallengeCode(),
                verification.getDocumentFrontMediaId(),
                verification.getDocumentBackMediaId(),
                verification.getSelfieMediaId(),
                verification.getSubmittedAt(),
                verification.getReviewedBy(),
                verification.getReviewedAt(),
                verification.getRejectionReason(),
                verification.getRegistryStatus(),
                check != null ? check.getProvider() : null,
                check != null ? check.getPersonType() : null,
                check != null ? check.getFullName() : null,
                check != null ? check.getDocumentExpiresOn() : null,
                check != null ? check.getCheckedAt() : null);
    }
}
