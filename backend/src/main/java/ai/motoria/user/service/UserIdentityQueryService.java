package ai.motoria.user.service;

import ai.motoria.common.port.UserIdentityPort;
import ai.motoria.common.port.UserIdentitySnapshot;
import ai.motoria.user.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the shared {@link UserIdentityPort} owned by the user module.
 */
@ApplicationScoped
public class UserIdentityQueryService implements UserIdentityPort {

    @Inject
    UserProfileRepository userProfileRepository;

    @Override
    public Optional<UserIdentitySnapshot> findIdentity(UUID userId) {
        return userProfileRepository.findByIdOptional(userId)
                .map(profile -> new UserIdentitySnapshot(
                        profile.getId(),
                        profile.getIdentityLevel().rank(),
                        profile.getIdNumberHash(),
                        profile.getCountryCode()));
    }
}
