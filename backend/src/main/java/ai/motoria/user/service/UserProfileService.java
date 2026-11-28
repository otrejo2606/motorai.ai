package ai.motoria.user.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.common.security.IdNumberHasher;
import ai.motoria.user.domain.IdType;
import ai.motoria.user.domain.UserProfile;
import ai.motoria.user.dto.UpdateProfileRequest;
import ai.motoria.user.dto.UserProfileResponse;
import ai.motoria.user.exception.UserErrorCodes;
import ai.motoria.user.mapper.UserMapper;
import ai.motoria.user.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class UserProfileService {

    private static final int MIN_ID_NUMBER_LENGTH = 5;

    @Inject
    UserProfileRepository userProfileRepository;

    @Inject
    UserProfileCreator userProfileCreator;

    @Inject
    UserMapper userMapper;

    @Inject
    IdNumberCipher idNumberCipher;

    @Inject
    IdNumberHasher idNumberHasher;

    @Inject
    CurrentUser currentUser;

    @ConfigProperty(name = "motoria.media.default-country", defaultValue = "CR")
    String defaultCountry;

    /**
     * Returns the caller's profile, creating it at LEVEL_1 on first access.
     */
    @Transactional
    public UserProfileResponse getMine() {
        return toResponse(findOrCreate(currentUser.id()));
    }

    @Audited
    @Transactional
    public UserProfileResponse updateMine(UpdateProfileRequest request) {
        UserProfile profile = findOrCreate(currentUser.id());
        profile.updateContact(request.fullName().trim(), blankToNull(request.phone()), request.countryCode());
        if (request.idNumber() != null && !request.idNumber().isBlank()) {
            updateIdentityNumber(profile, request.idType(), request.idNumber());
        } else if (request.idType() != null && profile.hasIdentityNumber() && !profile.isIdentityNumberLocked()) {
            profile.updateIdentityNumber(idNumberCipher.encrypt(request.idType().name()), profile.getIdNumberEnc(),
                    profile.getIdNumberHash(), profile.getIdNumberLast4());
        }
        return toResponse(profile);
    }

    private void updateIdentityNumber(UserProfile profile, IdType requestedType, String idNumber) {
        String normalized = IdNumberHasher.normalize(idNumber);
        if (normalized.length() < MIN_ID_NUMBER_LENGTH) {
            throw new InvalidRequestException(UserErrorCodes.PROFILE_ID_NUMBER_INVALID, "The identity number is too short");
        }
        IdType idType = requestedType != null ? requestedType : decryptIdType(profile);
        if (idType == null) {
            throw new InvalidRequestException(UserErrorCodes.PROFILE_ID_TYPE_REQUIRED, "The identity type is required");
        }
        String hash = idNumberHasher.hash(normalized);
        if (profile.isIdentityNumberLocked() && !Objects.equals(hash, profile.getIdNumberHash())) {
            throw new BusinessRuleException(UserErrorCodes.PROFILE_ID_NUMBER_LOCKED,
                    "The identity number cannot change after the identity was verified");
        }
        profile.updateIdentityNumber(
                idNumberCipher.encrypt(idType.name()),
                idNumberCipher.encrypt(normalized),
                hash,
                normalized.substring(normalized.length() - 4));
    }

    public UserProfile findOrCreate(UUID userId) {
        return userProfileRepository.findByIdOptional(userId)
                .orElseGet(() -> createOrLoad(userId));
    }

    /**
     * Creates the profile in its own transaction; if a concurrent first request created it, re-reads it.
     */
    UserProfile createOrLoad(UUID userId) {
        try {
            userProfileCreator.create(userId, currentUser.email().orElse(null), defaultCountry);
        } catch (PersistenceException exception) {
            if (!UniqueViolations.isUniqueViolation(exception)) {
                throw exception;
            }
        }
        return userProfileRepository.findByIdOptional(userId)
                .orElseThrow(() -> new InvalidStateException(UserErrorCodes.PROFILE_CONCURRENT_CREATION,
                        "The profile could not be created; try again"));
    }

    public UserProfileResponse toResponse(UserProfile profile) {
        return userMapper.toResponse(profile, decryptIdType(profile));
    }

    public IdType decryptIdType(UserProfile profile) {
        if (profile.getIdTypeEnc() == null) {
            return null;
        }
        try {
            return IdType.valueOf(idNumberCipher.decrypt(profile.getIdTypeEnc()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
