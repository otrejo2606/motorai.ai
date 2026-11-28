package ai.motoria.user.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.user.domain.Consent;
import ai.motoria.user.domain.ConsentLedger;
import ai.motoria.user.domain.ConsentType;
import ai.motoria.user.domain.UserProfile;
import ai.motoria.user.dto.ConsentRequest;
import ai.motoria.user.dto.ConsentResponse;
import ai.motoria.user.mapper.UserMapper;
import ai.motoria.user.repository.ConsentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned and revocable consents (Ley 8968). Each decision is a new row; revoking is a decision with granted=false.
 * The version in force comes from motoria.consent.text-version (optionally overridden per type with
 * motoria.consent.text-version.TYPE).
 */
@ApplicationScoped
public class ConsentService {

    private static final String VERSION_PROPERTY = "motoria.consent.text-version";

    @Inject
    ConsentRepository consentRepository;

    @Inject
    UserProfileService userProfileService;

    @Inject
    UserMapper userMapper;

    @Inject
    CurrentUser currentUser;

    @ConfigProperty(name = VERSION_PROPERTY, defaultValue = "1")
    String defaultTextVersion;

    /**
     * One item per consent type (always the full catalog), as a single page.
     */
    public PageResponse<ConsentResponse> listMine() {
        UUID userId = currentUser.id();
        Map<ConsentType, Consent> current = ConsentLedger.current(consentRepository.listByUser(userId));
        List<ConsentResponse> items = Arrays.stream(ConsentType.values())
                .map(type -> userMapper.toResponse(type, currentTextVersion(type), current.get(type)))
                .toList();
        return new PageResponse<>(items, 0, items.size(), items.size());
    }

    @Audited
    @Transactional
    public ConsentResponse record(ConsentRequest request, String ipAddress) {
        UserProfile profile = userProfileService.findOrCreate(currentUser.id());
        Consent consent = Consent.decide(
                profile.getId(),
                request.type(),
                request.textVersion(),
                request.granted(),
                Instant.now(),
                ipAddress);
        consentRepository.persist(consent);
        return userMapper.toResponse(request.type(), currentTextVersion(request.type()), consent);
    }

    String currentTextVersion(ConsentType type) {
        return ConfigProvider.getConfig()
                .getOptionalValue(VERSION_PROPERTY + "." + type.name(), String.class)
                .orElse(defaultTextVersion);
    }
}
