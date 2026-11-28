package ai.motoria.vehicle.domain;

import ai.motoria.common.port.ExtractedValue;
import ai.motoria.common.port.RegistryExtraction;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryReviewPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final List<VehicleAttributeKey> REQUIRED = List.of(
            VehicleAttributeKey.BRAND, VehicleAttributeKey.MODEL, VehicleAttributeKey.YEAR);
    private static final Function<String, Optional<String>> NORMALIZER =
            plate -> Optional.of(plate.replace("-", "").toUpperCase());

    private final RegistryReviewPolicy manualPolicy = new RegistryReviewPolicy(REQUIRED, 0.85, Duration.ofDays(30), false);
    private final RegistryReviewPolicy autoPolicy = new RegistryReviewPolicy(REQUIRED, 0.85, Duration.ofDays(30), true);

    @Test
    void failedExtractionShouldGoToAnalystWithoutData() {
        ReviewDecision decision = manualPolicy.decide(RegistryExtraction.failed(UUID.randomUUID(), "AI_UNAVAILABLE", 2),
                "ABC123", NORMALIZER, TODAY);

        assertFalse(decision.applyData());
        assertEquals(ReviewReason.EXTRACTION_FAILED, decision.reason());
    }

    @Test
    void differentPlateShouldNotApplyAnyData() {
        ReviewDecision decision = autoPolicy.decide(extraction("XYZ-999", 0.99, TODAY), "ABC123", NORMALIZER, TODAY);

        assertFalse(decision.applyData());
        assertEquals(ReviewReason.PLATE_MISMATCH, decision.reason());
    }

    @Test
    void lowConfidenceRequiredFieldShouldGoToAnalyst() {
        ReviewDecision decision = autoPolicy.decide(extraction("ABC-123", 0.50, TODAY), "ABC123", NORMALIZER, TODAY);

        assertTrue(decision.applyData());
        assertEquals(ReviewReason.LOW_CONFIDENCE, decision.reason());
    }

    @Test
    void oldDocumentShouldGoToAnalyst() {
        ReviewDecision decision = autoPolicy.decide(extraction("ABC-123", 0.99, TODAY.minusDays(45)), "ABC123", NORMALIZER, TODAY);

        assertEquals(ReviewReason.DOCUMENT_TOO_OLD, decision.reason());
    }

    @Test
    void missingRequiredFieldShouldGoToAnalyst() {
        RegistryExtraction extraction = new RegistryExtraction(UUID.randomUUID(), true, null,
                new ExtractedValue("DOCUMENT_PLATE", "ABC123", 1, 0.99), TODAY,
                List.of(new ExtractedValue("BRAND", "TOYOTA", 1, 0.99)), List.of(), 0, 2);

        assertEquals(ReviewReason.MISSING_REQUIRED, autoPolicy.decide(extraction, "ABC123", NORMALIZER, TODAY).reason());
    }

    @Test
    void cleanExtractionStillGoesToAnalystWhileAutoApprovalIsDisabled() {
        ReviewDecision decision = manualPolicy.decide(extraction("ABC-123", 0.99, TODAY), "ABC123", NORMALIZER, TODAY);

        assertTrue(decision.applyData());
        assertEquals(ReviewReason.MANUAL_REVIEW_DEFAULT, decision.reason());
    }

    @Test
    void cleanExtractionIsAutoApprovedOnlyWhenEnabled() {
        ReviewDecision decision = autoPolicy.decide(extraction("ABC-123", 0.99, TODAY), "ABC123", NORMALIZER, TODAY);

        assertTrue(decision.autoApprove());
    }

    private RegistryExtraction extraction(String plate, double confidence, LocalDate documentDate) {
        return new RegistryExtraction(UUID.randomUUID(), true, null,
                new ExtractedValue("DOCUMENT_PLATE", plate, 1, 0.99),
                documentDate,
                List.of(
                        new ExtractedValue("BRAND", "TOYOTA", 1, confidence),
                        new ExtractedValue("MODEL", "COROLLA", 1, 0.99),
                        new ExtractedValue("YEAR", "2021", 1, 0.99)),
                List.of(), 0, 2);
    }
}
