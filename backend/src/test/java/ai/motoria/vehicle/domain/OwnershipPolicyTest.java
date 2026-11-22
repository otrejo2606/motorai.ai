package ai.motoria.vehicle.domain;

import ai.motoria.common.port.OwnershipMatch;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnershipPolicyTest {

    @Test
    void sameHashShouldMatch() {
        assertEquals(OwnershipMatch.MATCH, OwnershipPolicy.compare("h1", "h1"));
    }

    @Test
    void differentHashShouldNotMatch() {
        assertEquals(OwnershipMatch.MISMATCH, OwnershipPolicy.compare("h1", "h2"));
    }

    @Test
    void missingDataShouldBeUnknown() {
        assertEquals(OwnershipMatch.UNKNOWN, OwnershipPolicy.compare(null, "h2"));
        assertEquals(OwnershipMatch.UNKNOWN, OwnershipPolicy.compare("h1", null));
        assertEquals(OwnershipMatch.UNKNOWN, OwnershipPolicy.compare("", "h1"));
    }
}
