package ai.motoria.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsentLedgerTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    void latestDecisionShouldWin() {
        Instant now = Instant.now();
        List<Consent> decisions = List.of(
                Consent.decide(userId, ConsentType.SHARE_BANK, "2026-11", true, now.minusSeconds(60), "10.0.0.1"),
                Consent.decide(userId, ConsentType.SHARE_BANK, "2026-11", false, now, "10.0.0.1"));

        assertFalse(ConsentLedger.isGranted(decisions, ConsentType.SHARE_BANK));
    }

    @Test
    void decisionsAreIndependentPerType() {
        Instant now = Instant.now();
        List<Consent> decisions = List.of(
                Consent.decide(userId, ConsentType.DATA_PROCESSING, "2026-11", true, now, null),
                Consent.decide(userId, ConsentType.MODEL_TRAINING, "2026-11", false, now, null));

        assertTrue(ConsentLedger.isGranted(decisions, ConsentType.DATA_PROCESSING));
        assertFalse(ConsentLedger.isGranted(decisions, ConsentType.MODEL_TRAINING));
        assertFalse(ConsentLedger.isGranted(decisions, ConsentType.PHOTO_LOCATION));
        assertEquals(2, ConsentLedger.current(decisions).size());
    }

    @Test
    void orderOfInputShouldNotMatter() {
        Instant now = Instant.now();
        List<Consent> decisions = List.of(
                Consent.decide(userId, ConsentType.SHARE_INSURANCE_BROKER, "2026-12", true, now, null),
                Consent.decide(userId, ConsentType.SHARE_INSURANCE_BROKER, "2026-11", false, now.minusSeconds(3600), null));

        assertTrue(ConsentLedger.isGranted(decisions, ConsentType.SHARE_INSURANCE_BROKER));
        assertEquals("2026-12", ConsentLedger.current(decisions).get(ConsentType.SHARE_INSURANCE_BROKER).getTextVersion());
    }
}
