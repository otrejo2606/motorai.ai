package ai.motoria.user.service;

import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.common.security.IdNumberHasher;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdNumberCipherTest {

    /**
     * Random key generated per test run; no key material lives in the repository.
     */
    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void encryptThenDecryptShouldRoundTripWithRandomIv() {
        IdNumberCipher cipher = new IdNumberCipher(Optional.of(randomKey()));

        String first = cipher.encrypt("102340567");
        String second = cipher.encrypt("102340567");

        assertTrue(first.startsWith("v1:"));
        assertNotEquals(first, second);
        assertEquals("102340567", cipher.decrypt(first));
    }

    @Test
    void missingKeyShouldFailWithStableCode() {
        IdNumberCipher cipher = new IdNumberCipher(Optional.empty());

        DependencyUnavailableException exception = assertThrows(DependencyUnavailableException.class,
                () -> cipher.encrypt("102340567"));
        assertEquals("USER_CRYPTO_KEY_MISSING", exception.getCode());
    }

    @Test
    void hashShouldIgnoreSeparators() {
        IdNumberHasher hasher = new IdNumberHasher(Optional.of(randomKey()));

        assertEquals(hasher.hash("1-0234-0567"), hasher.hash("102340567"));
        assertNotEquals(hasher.hash("102340567"), hasher.hash("102340568"));
    }
}
