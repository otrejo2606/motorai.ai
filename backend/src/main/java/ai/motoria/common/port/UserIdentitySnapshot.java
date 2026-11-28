package ai.motoria.common.port;

import java.util.UUID;

/**
 * @param identityRank 1, 2 or 3 (LEVEL_1, LEVEL_2, LEVEL_3)
 * @param idNumberHash keyed hash of the identity number, null when the profile has none
 */
public record UserIdentitySnapshot(
        UUID userId,
        int identityRank,
        String idNumberHash,
        String countryCode) {
}
