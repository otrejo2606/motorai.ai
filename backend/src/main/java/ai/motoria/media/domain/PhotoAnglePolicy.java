package ai.motoria.media.domain;

import ai.motoria.common.port.PhotoAngleCheckResult;

import java.util.Objects;

/**
 * Decision on the classifier answer (design D4 of add-captura-guiada-fotos), with configurable thresholds:
 * <ul>
 *   <li>predicted slot equal to the expected one with confidence at least {@code acceptThreshold}: MATCH;</li>
 *   <li>predicted slot different (another slot or OTHER) with confidence at least {@code rejectThreshold}: MISMATCH;</li>
 *   <li>anything else: UNCERTAIN (the analyst decides).</li>
 * </ul>
 * A failure of the classifier is UNAVAILABLE and is decided by the caller, not here.
 */
public final class PhotoAnglePolicy {

    private PhotoAnglePolicy() {
    }

    /**
     * @return {@link PhotoAngleCheckResult#MATCH}, {@link PhotoAngleCheckResult#MISMATCH} or
     *         {@link PhotoAngleCheckResult#UNCERTAIN}
     */
    public static String decide(String expectedSlot, String predictedSlot, double confidence,
                                double acceptThreshold, double rejectThreshold) {
        boolean sameSlot = Objects.equals(expectedSlot, predictedSlot);
        if (sameSlot && confidence >= acceptThreshold) {
            return PhotoAngleCheckResult.MATCH;
        }
        if (!sameSlot && confidence >= rejectThreshold) {
            return PhotoAngleCheckResult.MISMATCH;
        }
        return PhotoAngleCheckResult.UNCERTAIN;
    }
}
