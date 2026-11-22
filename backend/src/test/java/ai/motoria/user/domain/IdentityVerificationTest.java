package ai.motoria.user.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityVerificationTest {

    @Test
    void submitThenApproveShouldWork() {
        Instant now = Instant.now();
        IdentityVerification verification = issued(now.plus(Duration.ofMinutes(30)));

        verification.submit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now);
        verification.approve("analyst-1", now);

        assertEquals(IdentityVerificationStatus.APPROVED, verification.getStatus());
        assertEquals("analyst-1", verification.getReviewedBy());
    }

    @Test
    void submitAfterExpiryShouldFail() {
        Instant now = Instant.now();
        IdentityVerification verification = issued(now.minusSeconds(1));

        assertThrows(IllegalStateException.class,
                () -> verification.submit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now));
    }

    @Test
    void approveWithoutSubmissionShouldFail() {
        IdentityVerification verification = issued(Instant.now().plusSeconds(60));

        assertThrows(IllegalStateException.class, () -> verification.approve("analyst-1", Instant.now()));
    }

    @Test
    void rejectShouldKeepReason() {
        Instant now = Instant.now();
        IdentityVerification verification = issued(now.plusSeconds(600));
        verification.submit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), now);

        verification.reject("analyst-1", "Selfie sin codigo", now);

        assertEquals(IdentityVerificationStatus.REJECTED, verification.getStatus());
        assertEquals("Selfie sin codigo", verification.getRejectionReason());
    }

    @Test
    void identityLevelNeverDecreases() {
        UserProfile profile = UserProfile.create(UUID.randomUUID(), null, "CR");
        profile.raiseIdentityLevel(IdentityLevel.LEVEL_2, "MANUAL", "ref");
        profile.raiseIdentityLevel(IdentityLevel.LEVEL_1, "MANUAL", "ref2");

        assertEquals(IdentityLevel.LEVEL_2, profile.getIdentityLevel());
    }

    private IdentityVerification issued(Instant expiresAt) {
        return IdentityVerification.issue(UUID.randomUUID(), "MANUAL", "ABC234", expiresAt);
    }
}
