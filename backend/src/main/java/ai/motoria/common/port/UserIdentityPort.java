package ai.motoria.common.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by {@code user}. Gives other modules the identity level and the keyed hash of the
 * verified identity number, never the number itself.
 */
public interface UserIdentityPort {

    Optional<UserIdentitySnapshot> findIdentity(UUID userId);
}
