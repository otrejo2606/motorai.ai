package ai.motoria.user.integration;

import java.time.Instant;

/**
 * Identity verification provider (base task 1.1 pending). F1 uses the manual adapter: a one-time code that the
 * user shows in a selfie with the document, reviewed by a BACKOFFICE analyst.
 */
public interface IdentityVerificationPort {

    String providerName();

    IdentityChallenge issueChallenge(Instant now);

    /**
     * True when the case is decided by an analyst (manual adapter), false when the provider decides.
     */
    boolean requiresManualReview();
}
