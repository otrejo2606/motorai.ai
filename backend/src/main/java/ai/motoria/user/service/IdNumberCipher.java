package ai.motoria.user.service;

import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.user.exception.UserErrorCodes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * AES-256-GCM encryption at rest for the identity number and type. The key is read from the environment
 * (MOTORIA_USER_CRYPTO_KEY, base64 of 32 bytes) and is never stored in the repository.
 * Stored format: "v1:" + base64(iv(12) + ciphertext + tag).
 */
@ApplicationScoped
public class IdNumberCipher {

    private static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();
    private final Optional<String> base64Key;

    @Inject
    public IdNumberCipher(@ConfigProperty(name = "motoria.user.crypto.key") Optional<String> base64Key) {
        this.base64Key = base64Key;
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new DependencyUnavailableException(UserErrorCodes.USER_CRYPTO_FAILED, "Unable to encrypt the value", exception);
        }
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new DependencyUnavailableException(UserErrorCodes.USER_CRYPTO_FAILED, "Unknown ciphertext format");
        }
        byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        if (payload.length <= IV_LENGTH) {
            throw new DependencyUnavailableException(UserErrorCodes.USER_CRYPTO_FAILED, "Ciphertext too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(), "AES"), new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new DependencyUnavailableException(UserErrorCodes.USER_CRYPTO_FAILED, "Unable to decrypt the value", exception);
        }
    }

    private byte[] key() {
        byte[] key = base64Key.filter(value -> !value.isBlank())
                .map(value -> Base64.getDecoder().decode(value.trim()))
                .orElseThrow(() -> new DependencyUnavailableException(
                        UserErrorCodes.USER_CRYPTO_KEY_MISSING, "The encryption key is not configured"));
        if (key.length != KEY_LENGTH) {
            throw new DependencyUnavailableException(UserErrorCodes.USER_CRYPTO_KEY_MISSING, "The encryption key must be 32 bytes");
        }
        return key;
    }
}
