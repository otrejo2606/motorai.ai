package ai.motoria.user.service;

import ai.motoria.common.security.CurrentUser;
import ai.motoria.user.domain.UserProfile;
import ai.motoria.user.repository.UserProfileRepository;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two first requests of the same user: the loser re-reads the profile instead of returning 500.
 */
class UserProfileCreationRaceTest {

    @Test
    void duplicateProfileReReadsTheWinner() {
        UUID userId = UUID.randomUUID();
        UserProfileRepository repository = mock(UserProfileRepository.class);
        UserProfileCreator creator = mock(UserProfileCreator.class);
        CurrentUser currentUser = mock(CurrentUser.class);
        UserProfile winner = UserProfile.create(userId, null, "CR");

        when(currentUser.email()).thenReturn(Optional.empty());
        doThrow(new PersistenceException("flush failed", new ConstraintViolationException(
                "duplicate", new SQLException("duplicate key", "23505"), "user_profile_pkey")))
                .when(creator).create(eq(userId), any(), eq("CR"));
        when(repository.findByIdOptional(userId)).thenReturn(Optional.of(winner));

        UserProfileService service = new UserProfileService();
        service.userProfileRepository = repository;
        service.userProfileCreator = creator;
        service.currentUser = currentUser;
        service.defaultCountry = "CR";

        assertSame(winner, service.createOrLoad(userId));
    }
}
