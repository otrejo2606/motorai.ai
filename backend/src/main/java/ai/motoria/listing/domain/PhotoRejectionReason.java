package ai.motoria.listing.domain;

/**
 * Translatable reason shown to the seller when a photo must be repeated. WRONG_ANGLE and NOT_THE_VEHICLE also come
 * from the classifier (another slot, or OTHER); the rest only from the analyst.
 */
public enum PhotoRejectionReason {
    WRONG_ANGLE,
    BLURRY,
    TOO_DARK,
    OBSTRUCTED,
    NOT_THE_VEHICLE,
    OTHER
}
