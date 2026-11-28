package ai.motoria.user.service;

import ai.motoria.user.domain.UserProfile;
import ai.motoria.user.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Creates the profile in its own transaction so two first requests of the same user (user_profile_pkey) do not
 * fail the caller's transaction; the caller re-reads the profile that won.
 */
@ApplicationScoped
public class UserProfileCreator {

    @Inject
    UserProfileRepository userProfileRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void create(UUID userId, String email, String countryCode) {
        userProfileRepository.persistAndFlush(UserProfile.create(userId, email, countryCode));
    }
}
