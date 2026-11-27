package ai.motoria.media.integration;

/**
 * Answer of the classifier: the top prediction only (the other scores are not stored).
 *
 * @param predictedSlot one of the 14 protocol slots or OTHER
 * @param confidence    between 0 and 1
 * @param modelVersion  version of the model that answered (stored with the result)
 * @param vin           VIN read in the photo, only when the service has OCR (owner gate 0.3); null otherwise
 * @param odometerKm    odometer in kilometres, only with OCR; null otherwise
 * @param latencyMs     measured by the adapter
 */
public record PhotoAngleClassification(
        String predictedSlot,
        double confidence,
        String modelVersion,
        String vin,
        Integer odometerKm,
        long latencyMs) {
}
