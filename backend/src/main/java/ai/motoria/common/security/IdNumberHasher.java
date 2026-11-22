package ai.motoria.common.security;

import ai.motoria.common.exception.DependencyUnavailableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Keyed hash (HMAC-SHA256) of an identity number, shared by {@code user} (seller cedula) and
 * {@code vehicle} (registry owner) so both can be compared without storing the number in clear.
 * A keyed hash is used because a plain SHA-256 of a 9 digit cedula is trivially brute-forced.
 * The key comes from the environment (MOTORIA_IDENTITY_HASH_KEY, base64), never from the repo.
 */
@ApplicationScoped
public class IdNumberHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final Optional<String> base64Key;

    @Inject
    public IdNumberHasher(@ConfigProperty(name = "motoria.identity.hash-key") Optional<String> base64Key) {
        this.base64Key = base64Key;
    }

    /**
     * Upper case and keep only letters and digits, so "1-0234-0567" and "102340567" match.
     */
    public static String normalize(String idNumber) {
        if (idNumber == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (char character : idNumber.toUpperCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'A' && character <= 'Z') || (character >= '0' && character <= '9')) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    public String hash(String idNumber) {
        String normalized = normalize(idNumber);
        if (normalized.isEmpty()) {
            return null;
        }
        byte[] key = base64Key.filter(value -> !value.isBlank())
                .map(value -> Base64.getDecoder().decode(value.trim()))
                .orElseThrow(() -> new DependencyUnavailableException(
                        "IDENTITY_HASH_KEY_MISSING", "The identity hash key is not configured"));
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new DependencyUnavailableException("IDENTITY_HASH_FAILED", "Unable to hash the identity number", exception);
        }
    }
}
