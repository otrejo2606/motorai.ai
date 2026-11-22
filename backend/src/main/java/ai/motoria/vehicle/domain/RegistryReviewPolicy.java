package ai.motoria.vehicle.domain;

import ai.motoria.common.port.ExtractedValue;
import ai.motoria.common.port.RegistryExtraction;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Decides whether an extraction goes to the analyst (design D1 step 4, D4): failed extraction or different plate
 * (nothing applied), missing required values, a required value under the confidence threshold, a document older than
 * the configured age, or automatic approval disabled (always, until precision is measured).
 */
public final class RegistryReviewPolicy {

    private final Collection<VehicleAttributeKey> requiredKeys;
    private final double minConfidence;
    private final Duration documentMaxAge;
    private final boolean autoApproveEnabled;

    public RegistryReviewPolicy(Collection<VehicleAttributeKey> requiredKeys, double minConfidence,
                                Duration documentMaxAge, boolean autoApproveEnabled) {
        this.requiredKeys = requiredKeys;
        this.minConfidence = minConfidence;
        this.documentMaxAge = documentMaxAge;
        this.autoApproveEnabled = autoApproveEnabled;
    }

    /**
     * @param plateNormalizer normalizes a plate of the vehicle's country; empty when the text is not a valid plate
     */
    public ReviewDecision decide(RegistryExtraction extraction, String vehiclePlate,
                                 Function<String, Optional<String>> plateNormalizer, LocalDate today) {
        if (!extraction.succeeded()) {
            return new ReviewDecision(false, ReviewReason.EXTRACTION_FAILED);
        }
        ExtractedValue documentPlate = extraction.documentPlate();
        if (documentPlate != null) {
            Optional<String> normalized = plateNormalizer.apply(documentPlate.value());
            if (normalized.isEmpty() || !normalized.get().equals(vehiclePlate)) {
                return new ReviewDecision(false, ReviewReason.PLATE_MISMATCH);
            }
        }

        Map<String, ExtractedValue> byKey = extraction.attributes().stream()
                .collect(Collectors.toMap(ExtractedValue::key, Function.identity(), (first, second) -> first));
        boolean missing = documentPlate == null || extraction.documentDate() == null
                || requiredKeys.stream().anyMatch(key -> !byKey.containsKey(key.name()));
        if (missing) {
            return new ReviewDecision(true, ReviewReason.MISSING_REQUIRED);
        }
        boolean lowConfidence = documentPlate.confidence() < minConfidence
                || requiredKeys.stream().anyMatch(key -> byKey.get(key.name()).confidence() < minConfidence);
        if (lowConfidence) {
            return new ReviewDecision(true, ReviewReason.LOW_CONFIDENCE);
        }
        long maxDays = documentMaxAge.toDays();
        if (extraction.documentDate().plusDays(maxDays).isBefore(today)) {
            return new ReviewDecision(true, ReviewReason.DOCUMENT_TOO_OLD);
        }
        return autoApproveEnabled
                ? new ReviewDecision(true, null)
                : new ReviewDecision(true, ReviewReason.MANUAL_REVIEW_DEFAULT);
    }
}
