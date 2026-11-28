package ai.motoria.user.domain;

/**
 * Result of comparing the profile with the person registry (Credid). Informative: the analyst still decides.
 */
public enum RegistryMatchStatus {
    /** The registry has the document, it is not expired and the profile name matches. */
    MATCH,
    /** The registry has the document but the name does not match the profile. */
    NAME_MISMATCH,
    /** The registry has no person with that document. */
    NOT_FOUND,
    /** The registry reports the document as expired. */
    DOCUMENT_EXPIRED,
    /** The registry could not be queried (failure, timeout or daily quota); the analyst can retry. */
    UNAVAILABLE,
    /** The document type cannot be queried in the registry (passport). */
    NOT_APPLICABLE
}
