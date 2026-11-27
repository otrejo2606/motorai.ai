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
    EXTRACTION_FAILED,
    /**
     * Data from the registry agreement (Credid) waiting for the analyst because automatic approval of agreement data
     * is off (motoria.vehicle.registry.agreement-auto-approve=false).
     */
    AGREEMENT_REVIEW
}
