package ai.motoria.media.domain;

import ai.motoria.common.port.PhotoAngleCheckResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Decision by thresholds (design D4 of add-captura-guiada-fotos): accept 0.80, reject 0.90.
 */
class PhotoAnglePolicyTest {

    private static final double ACCEPT = 0.80;
    private static final double REJECT = 0.90;

    @Test
    void sameSlotWithEnoughConfidenceShouldMatch() {
        assertEquals(PhotoAngleCheckResult.MATCH, PhotoAnglePolicy.decide("FRONT", "FRONT", 0.80, ACCEPT, REJECT));
        assertEquals(PhotoAngleCheckResult.MATCH, PhotoAnglePolicy.decide("VIN", "VIN", 0.99, ACCEPT, REJECT));
    }

    @Test
    void sameSlotWithLowConfidenceShouldBeUncertain() {
        assertEquals(PhotoAngleCheckResult.UNCERTAIN, PhotoAnglePolicy.decide("FRONT", "FRONT", 0.79, ACCEPT, REJECT));
    }

    @Test
    void anotherSlotWithHighConfidenceShouldMismatch() {
        assertEquals(PhotoAngleCheckResult.MISMATCH, PhotoAnglePolicy.decide("FRONT", "REAR", 0.90, ACCEPT, REJECT));
        assertEquals(PhotoAngleCheckResult.MISMATCH, PhotoAnglePolicy.decide("ENGINE", "OTHER", 0.97, ACCEPT, REJECT));
    }

    @Test
    void anotherSlotWithMediumConfidenceShouldBeUncertain() {
        assertEquals(PhotoAngleCheckResult.UNCERTAIN, PhotoAnglePolicy.decide("FRONT_LEFT", "FRONT", 0.85, ACCEPT, REJECT));
    }

    @Test
    void missingPredictionShouldBeUncertain() {
        assertEquals(PhotoAngleCheckResult.UNCERTAIN, PhotoAnglePolicy.decide("FRONT", null, 0.99, ACCEPT, 1.01));
    }
}
