package ai.motoria.common.port;

/**
 * Result of the angle check of one listing photo (add-captura-guiada-fotos), decided by {@code media} from the local
 * classifier. Never carries the image nor the raw answer of the classifier.
 *
 * @param status        NOT_CHECKED (check off or slot not configured), MATCH, MISMATCH, UNCERTAIN or UNAVAILABLE
 * @param predictedSlot slot the classifier sees (one of the 14 protocol slots or OTHER); null when not available
 * @param confidence    confidence of the predicted slot, between 0 and 1; null when not available
 * @param modelVersion  version of the classifier model that answered; null when not available
 * @param latencyMs     time spent in the classifier call; null when it was not called
 * @param vin           VIN read in the photo (only when the classifier service has OCR); null otherwise
 * @param odometerKm    odometer read in the photo, in kilometres (only with OCR); null otherwise
 * @param errorCode     stable code when UNAVAILABLE (PHOTO_CLASSIFIER_*); null otherwise
 */
public record PhotoAngleCheckResult(
        String status,
        String predictedSlot,
        Double confidence,
        String modelVersion,
        Long latencyMs,
        String vin,
        Integer odometerKm,
        String errorCode) {

    public static final String NOT_CHECKED = "NOT_CHECKED";
    public static final String MATCH = "MATCH";
    public static final String MISMATCH = "MISMATCH";
    public static final String UNCERTAIN = "UNCERTAIN";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String OTHER_SLOT = "OTHER";
    /** Same value as MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE, for callers outside the media module. */
    public static final String ERROR_UNAVAILABLE = "PHOTO_CLASSIFIER_UNAVAILABLE";

    public static PhotoAngleCheckResult notChecked() {
        return new PhotoAngleCheckResult(NOT_CHECKED, null, null, null, null, null, null, null);
    }

    public static PhotoAngleCheckResult unavailable(String errorCode, Long latencyMs) {
        return new PhotoAngleCheckResult(UNAVAILABLE, null, null, null, latencyMs, null, null, errorCode);
    }
}
