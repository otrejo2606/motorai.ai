package ai.motoria.user.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.user.domain.IdType;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.exception.QuotaExceededException;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.MediaSnapshot;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.user.domain.IdentityLevel;
import ai.motoria.user.domain.IdentityVerification;
import ai.motoria.user.domain.IdentityVerificationStatus;
import ai.motoria.user.domain.UserProfile;
import ai.motoria.user.dto.BackofficeIdentityVerificationResponse;
import ai.motoria.user.dto.IdentityChallengeResponse;
import ai.motoria.user.dto.IdentityVerificationResponse;
import ai.motoria.user.dto.SubmitIdentityVerificationRequest;
import ai.motoria.user.exception.UserErrorCodes;
import ai.motoria.user.integration.IdentityChallenge;
import ai.motoria.user.integration.IdentityVerificationPort;
import ai.motoria.user.mapper.UserMapper;
import ai.motoria.user.repository.IdentityVerificationRepository;
import ai.motoria.user.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manual identity verification (design D1) with a configurable daily quota of submissions for analysts.
 */
@ApplicationScoped
public class IdentityVerificationService {

    static final String IDENTITY_DOCUMENT_KIND = "IDENTITY_DOCUMENT";

    @Inject
    IdentityVerificationRepository identityVerificationRepository;

    @Inject
    UserProfileRepository userProfileRepository;

    @Inject
    UserProfileService userProfileService;

    @Inject
    IdentityVerificationPort identityVerificationPort;

    @Inject
    MediaPort mediaPort;

    @Inject
    UserMapper userMapper;

    @Inject
    CurrentUser currentUser;

    @ConfigProperty(name = "motoria.user.identity.manual-daily-quota", defaultValue = "50")
    int manualDailyQuota;

    @ConfigProperty(name = "motoria.user.identity.quota-zone", defaultValue = "America/Costa_Rica")
    String quotaZone;

    @Audited
    @Transactional
    public IdentityChallengeResponse start() {
        UserProfile profile = userProfileService.findOrCreate(currentUser.id());
        if (!profile.hasIdentityNumber()) {
            throw new BusinessRuleException(UserErrorCodes.PROFILE_INCOMPLETE,
                    "The profile needs the identity number before verifying the identity");
        }
        if (profile.getIdentityLevel().rank() >= IdentityLevel.LEVEL_2.rank()) {
            throw new BusinessRuleException(UserErrorCodes.IDENTITY_ALREADY_VERIFIED, "The identity is already verified");
        }
        if (!identityVerificationRepository.listByUserAndStatus(profile.getId(), IdentityVerificationStatus.SUBMITTED).isEmpty()) {
            throw new InvalidStateException(UserErrorCodes.IDENTITY_VERIFICATION_PENDING, "A verification is already waiting for review");
        }
        identityVerificationRepository.listByUserAndStatus(profile.getId(), IdentityVerificationStatus.CHALLENGE_ISSUED)
                .forEach(IdentityVerification::expire);

        IdentityChallenge challenge = identityVerificationPort.issueChallenge(Instant.now());
        IdentityVerification verification = IdentityVerification.issue(
                profile.getId(),
                identityVerificationPort.providerName(),
                challenge.code(),
                challenge.expiresAt());
        identityVerificationRepository.persist(verification);
        return userMapper.toChallengeResponse(verification);
    }

    @Audited
    @Transactional
    public IdentityVerificationResponse submit(UUID verificationId, SubmitIdentityVerificationRequest request) {
        UUID userId = currentUser.id();
        IdentityVerification verification = identityVerificationRepository.findByIdOptional(verificationId)
                .filter(candidate -> candidate.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException(UserErrorCodes.IDENTITY_VERIFICATION_NOT_FOUND,
                        "Identity verification not found"));
        if (verification.getStatus() != IdentityVerificationStatus.CHALLENGE_ISSUED) {
            throw new InvalidStateException(UserErrorCodes.IDENTITY_VERIFICATION_INVALID_STATE,
                    "The verification cannot be submitted in its current state");
        }
        Instant now = Instant.now();
        if (verification.isChallengeExpired(now)) {
            throw new BusinessRuleException(UserErrorCodes.IDENTITY_CHALLENGE_EXPIRED, "The challenge code expired; start again");
        }
        validateIdentityMedia(userId, request);
        if (identityVerificationPort.requiresManualReview()
                && identityVerificationRepository.countSubmittedSince(startOfToday()) >= manualDailyQuota) {
            throw new QuotaExceededException(UserErrorCodes.IDENTITY_MANUAL_QUOTA_EXHAUSTED,
                    "The daily capacity for manual verification is exhausted; try again tomorrow");
        }
        verification.submit(request.documentFrontMediaId(), request.documentBackMediaId(), request.selfieMediaId(), now);
        return userMapper.toResponse(verification);
    }

    public IdentityVerificationResponse latestMine() {
        return identityVerificationRepository.findLatestByUser(currentUser.id())
                .map(userMapper::toResponse)
                .orElseThrow(() -> new NotFoundException(UserErrorCodes.IDENTITY_VERIFICATION_NOT_FOUND,
                        "No identity verification yet"));
    }

    public PageResponse<BackofficeIdentityVerificationResponse> listForReview(IdentityVerificationStatus status, int page, int size) {
        IdentityVerificationStatus effective = status == null ? IdentityVerificationStatus.SUBMITTED : status;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<BackofficeIdentityVerificationResponse> items = identityVerificationRepository
                .pageByStatus(effective, safePage, safeSize).stream()
                .map(this::toBackofficeResponse)
                .toList();
        return new PageResponse<>(items, safePage, safeSize, identityVerificationRepository.countByStatus(effective));
    }

    @Audited
    @Transactional
    public BackofficeIdentityVerificationResponse approve(UUID verificationId) {
        IdentityVerification verification = findForReview(verificationId);
        String reviewer = currentUser.id().toString();
        try {
            verification.approve(reviewer, Instant.now());
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(UserErrorCodes.IDENTITY_VERIFICATION_INVALID_STATE, exception.getMessage());
        }
        UserProfile profile = userProfileRepository.findByIdOptional(verification.getUserId())
                .orElseThrow(() -> new NotFoundException(UserErrorCodes.PROFILE_NOT_FOUND, "Profile not found"));
        profile.raiseIdentityLevel(IdentityLevel.LEVEL_2, verification.getProvider(), verification.getId().toString());
        return userMapper.toBackofficeResponse(verification, profile, userProfileService.decryptIdType(profile));
    }

    @Audited
    @Transactional
    public BackofficeIdentityVerificationResponse reject(UUID verificationId, String reason) {
        IdentityVerification verification = findForReview(verificationId);
        try {
            verification.reject(currentUser.id().toString(), reason.trim(), Instant.now());
        } catch (IllegalStateException exception) {
            throw new InvalidStateException(UserErrorCodes.IDENTITY_VERIFICATION_INVALID_STATE, exception.getMessage());
        }
        return toBackofficeResponse(verification);
    }

    private IdentityVerification findForReview(UUID verificationId) {
        IdentityVerification verification = identityVerificationRepository.findByIdOptional(verificationId)
                .orElseThrow(() -> new NotFoundException(UserErrorCodes.IDENTITY_VERIFICATION_NOT_FOUND,
                        "Identity verification not found"));
        if (verification.getUserId().equals(currentUser.id())) {
            throw new ForbiddenOperationException(UserErrorCodes.IDENTITY_SELF_REVIEW_FORBIDDEN,
                    "An analyst cannot review their own verification");
        }
        return verification;
    }

    private void validateIdentityMedia(UUID userId, SubmitIdentityVerificationRequest request) {
        Set<UUID> distinct = new HashSet<>(List.of(
                request.documentFrontMediaId(), request.documentBackMediaId(), request.selfieMediaId()));
        if (distinct.size() != 3) {
            throw new BusinessRuleException(UserErrorCodes.IDENTITY_MEDIA_INVALID, "Three different files are required");
        }
        for (UUID mediaId : distinct) {
            MediaSnapshot media = mediaPort.findMedia(mediaId)
                    .orElseThrow(() -> new BusinessRuleException(UserErrorCodes.IDENTITY_MEDIA_INVALID, "Unknown file"));
            boolean valid = media.ownerId().equals(userId)
                    && IDENTITY_DOCUMENT_KIND.equals(media.kind())
                    && media.isConfirmed();
            if (!valid) {
                throw new BusinessRuleException(UserErrorCodes.IDENTITY_MEDIA_INVALID,
                        "Each file must be a confirmed identity document uploaded by the user");
            }
        }
    }

    private Instant startOfToday() {
        ZoneId zone = ZoneId.of(quotaZone);
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    private BackofficeIdentityVerificationResponse toBackofficeResponse(IdentityVerification verification) {
        UserProfile profile = userProfileRepository.findByIdOptional(verification.getUserId()).orElse(null);
        IdType idType = profile == null ? null : userProfileService.decryptIdType(profile);
        return userMapper.toBackofficeResponse(verification, profile, idType);
    }
}
