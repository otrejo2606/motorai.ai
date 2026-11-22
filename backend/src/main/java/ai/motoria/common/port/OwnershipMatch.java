package ai.motoria.common.port;

/**
 * Result of comparing the registry owner with the seller's verified identity number.
 */
public enum OwnershipMatch {
    MATCH,
    MISMATCH,
    UNKNOWN
}
