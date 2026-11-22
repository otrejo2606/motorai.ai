package ai.motoria.vehicle.domain;

/**
 * Why a registry document went to the analyst (design D1 step 4, D4).
 */
public enum ReviewReason {
    MANUAL_REVIEW_DEFAULT,
    LOW_CONFIDENCE,
    DOCUMENT_TOO_OLD,
    PLATE_MISMATCH,
    MISSING_REQUIRED,
    EXTRACTION_FAILED
}
