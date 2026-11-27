package ai.motoria.vehicle.domain;

/**
 * Anomaly found when the registry data is compared with the seller (the seller is told on screen and by a
 * notification). OWNER_MISMATCH: the registry owner is not the seller's verified identity number (the listing cannot be
 * sent to review). PLEDGE: the registry reports a pledge (it blocks the transfer, not the publication).
 */
public enum RegistryAnomaly {
    OWNER_MISMATCH,
    PLEDGE
}
