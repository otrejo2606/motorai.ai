package ai.motoria.vehicle.domain;

import ai.motoria.common.port.OwnershipMatch;

/**
 * Base requirement "Titularidad del vendedor": compares the registry owner's identity hash with the seller's.
 */
public final class OwnershipPolicy {

    private OwnershipPolicy() {
    }

    public static OwnershipMatch compare(String registryOwnerHash, String sellerIdNumberHash) {
        if (registryOwnerHash == null || registryOwnerHash.isBlank()
                || sellerIdNumberHash == null || sellerIdNumberHash.isBlank()) {
            return OwnershipMatch.UNKNOWN;
        }
        return registryOwnerHash.equals(sellerIdNumberHash) ? OwnershipMatch.MATCH : OwnershipMatch.MISMATCH;
    }
}
