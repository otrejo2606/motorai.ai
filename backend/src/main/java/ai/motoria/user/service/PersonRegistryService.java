package ai.motoria.user.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.QuotaExceededException;
import ai.motoria.common.security.IdNumberHasher;
import ai.motoria.user.domain.IdType;
import ai.motoria.user.domain.PersonRegistryAccess;
import ai.motoria.user.domain.PersonRegistryCheck;
import ai.motoria.user.domain.PersonRegistryPurpose;
import ai.motoria.user.domain.RegistryMatchStatus;
import ai.motoria.user.domain.UserProfile;
import ai.motoria.user.dto.PersonRegistryLookupRequest;
import ai.motoria.user.dto.PersonRegistryLookupResponse;
import ai.motoria.user.exception.UserErrorCodes;
import ai.motoria.user.integration.PersonRegistryPort;
import ai.motoria.user.integration.PersonRegistryRecord;
import ai.motoria.user.repository.PersonRegistryAccessRepository;
import ai.motoria.user.repository.PersonRegistryCheckRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Person registry checks (Credid): reuses a fresh check of the same number, enforces a daily quota of paid queries,
 * logs every use and compares the answer with the profile. The identity number never leaves this class in clear
 * except towards the provider.
 */
@ApplicationScoped
public class PersonRegistryService {

    static final int MIN_ID_NUMBER_LENGTH = 5;
    static final int MAX_ID_NUMBER_LENGTH = 20;

    @Inject
    PersonRegistryPort personRegistryPort;

    @Inject
    PersonRegistryCheckRepository checkRepository;

    @Inject
    PersonRegistryAccessRepository accessRepository;

    @Inject
    IdNumberHasher idNumberHasher;

    @Inject
    IdNumberCipher idNumberCipher;

    @Inject
    UserProfileService userProfileService;

    @ConfigProperty(name = "motoria.user.person-registry.cache-ttl", defaultValue = "P90D")
    Duration cacheTtl;

    @ConfigProperty(name = "motoria.user.person-registry.daily-quota", defaultValue = "200")
    int dailyQuota;

    @ConfigProperty(name = "motoria.user.identity.quota-zone", defaultValue = "America/Costa_Rica")
    String quotaZone;

    public record RegistryOutcome(RegistryMatchStatus status, PersonRegistryCheck check) {

        public UUID checkId() {
            return check == null ? null : check.getId();
        }
    }

    private record Obtained(PersonRegistryCheck check, boolean fromCache) {
    }

    /**
     * Compares the profile with the registry for an identity verification. Runs inside the caller's transaction and
     * never fails the caller: if the registry cannot answer the outcome is UNAVAILABLE and the analyst can retry.
     */
    public RegistryOutcome checkProfile(UserProfile profile, UUID verificationId) {
        if (!profile.hasIdentityNumber() || profile.getIdNumberHash() == null) {
            return new RegistryOutcome(RegistryMatchStatus.NOT_APPLICABLE, null);
        }
        try {
            IdType idType = userProfileService.decryptIdType(profile);
            if (!isSupported(idType)) {
                return new RegistryOutcome(RegistryMatchStatus.NOT_APPLICABLE, null);
            }
            String number = IdNumberHasher.normalize(idNumberCipher.decrypt(profile.getIdNumberEnc()));
            PersonRegistryCheck check = obtain(idType, number, profile.getIdNumberHash(), false).check();
            accessRepository.persist(PersonRegistryAccess.log(
                    check.getId(), PersonRegistryPurpose.IDENTITY_VERIFICATION, null, verificationId));
            return new RegistryOutcome(compare(profile.getFullName(), check), check);
        } catch (DependencyUnavailableException | QuotaExceededException exception) {
            Log.warnv("Person registry check skipped for verification {0}: {1} {2}",
                    verificationId, exception.getCode(), exception.getMessage());
            return new RegistryOutcome(RegistryMatchStatus.UNAVAILABLE, null);
        }
    }

    /**
     * Backoffice lookup of any document, with a mandatory reason logged with the analyst.
     */
    @Audited
    @Transactional
    public PersonRegistryLookupResponse lookup(PersonRegistryLookupRequest request) {
        if (!isSupported(request.idType())) {
            throw new BusinessRuleException(UserErrorCodes.PERSON_REGISTRY_ID_TYPE_UNSUPPORTED,
                    "Only national and resident identity documents can be looked up in the registry");
        }
        String number = IdNumberHasher.normalize(request.idNumber());
        if (number.length() < MIN_ID_NUMBER_LENGTH || number.length() > MAX_ID_NUMBER_LENGTH) {
            throw new InvalidRequestException(UserErrorCodes.PERSON_REGISTRY_ID_NUMBER_INVALID,
                    "The identity number must have between 5 and 20 letters or digits");
        }
        Obtained obtained = obtain(request.idType(), number, idNumberHasher.hash(number), Boolean.TRUE.equals(request.refresh()));
        PersonRegistryCheck check = obtained.check();
        accessRepository.persist(PersonRegistryAccess.log(
                check.getId(), PersonRegistryPurpose.BACKOFFICE_LOOKUP, request.reason().trim(), null));
        return new PersonRegistryLookupResponse(
                check.isFound(),
                check.getProvider(),
                check.getPersonType(),
                check.getFullName(),
                check.getDocumentExpiresOn(),
                check.isDocumentExpired(today()),
                check.getNationality(),
                check.getCheckedAt(),
                obtained.fromCache());
    }

    public Optional<PersonRegistryCheck> findCheck(UUID checkId) {
        return checkId == null ? Optional.empty() : checkRepository.findByIdOptional(checkId);
    }

    RegistryMatchStatus compare(String profileName, PersonRegistryCheck check) {
        if (!check.isFound()) {
            return RegistryMatchStatus.NOT_FOUND;
        }
        if (check.isDocumentExpired(today())) {
            return RegistryMatchStatus.DOCUMENT_EXPIRED;
        }
        return RegistryNameMatcher.matches(profileName, check.getFullName())
                ? RegistryMatchStatus.MATCH
                : RegistryMatchStatus.NAME_MISMATCH;
    }

    private Obtained obtain(IdType idType, String number, String hash, boolean refresh) {
        Instant now = Instant.now();
        String provider = personRegistryPort.providerName();
        if (!refresh) {
            Optional<PersonRegistryCheck> fresh = checkRepository.findFresh(hash, provider, now.minus(cacheTtl));
            if (fresh.isPresent()) {
                return new Obtained(fresh.get(), true);
            }
        }
        if (checkRepository.countCheckedSince(provider, startOfToday()) >= dailyQuota) {
            throw new QuotaExceededException(UserErrorCodes.PERSON_REGISTRY_QUOTA_EXHAUSTED,
                    "The daily quota of person registry queries is exhausted");
        }
        PersonRegistryRecord record = personRegistryPort.lookup(idType, number);
        PersonRegistryCheck check = PersonRegistryCheck.record(
                hash,
                provider,
                record.found(),
                record.found() ? record.personType() : null,
                record.found() ? record.fullName() : null,
                record.found() ? record.documentExpiresOn() : null,
                record.found() ? record.nationality() : null,
                record.providerReference(),
                now);
        checkRepository.persist(check);
        return new Obtained(check, false);
    }

    static boolean isSupported(IdType idType) {
        return idType == IdType.NATIONAL_ID || idType == IdType.RESIDENT_ID;
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(quotaZone));
    }

    private Instant startOfToday() {
        ZoneId zone = ZoneId.of(quotaZone);
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }
}
