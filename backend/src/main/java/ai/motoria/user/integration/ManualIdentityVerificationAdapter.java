package ai.motoria.user.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Manual verification (design D1): issues a 6 character one-time code without ambiguous characters.
 */
@ApplicationScoped
public class ManualIdentityVerificationAdapter implements IdentityVerificationPort {

    public static final String PROVIDER = "MANUAL";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();
    private final Duration challengeTtl;

    @Inject
    public ManualIdentityVerificationAdapter(
            @ConfigProperty(name = "motoria.user.identity.challenge-ttl", defaultValue = "PT30M") Duration challengeTtl) {
        this.challengeTtl = challengeTtl;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public IdentityChallenge issueChallenge(Instant now) {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new IdentityChallenge(code.toString(), now.plus(challengeTtl));
    }

    @Override
    public boolean requiresManualReview() {
        return true;
    }
}
