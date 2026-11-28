package ai.motoria.media.integration;

import java.time.Duration;
import java.util.Optional;

/**
 * Object storage (S3). Private objects live in the private bucket; metadata-free public copies in the public bucket.
 */
public interface ObjectStoragePort {

    PresignedUrl presignUpload(String key, String contentType, long contentLength, Duration ttl);

    PresignedUrl presignRead(String key, Duration ttl);

    Optional<Long> objectSize(String key);

    /**
     * Reads a private object; fails when it is larger than {@code maxBytes}.
     */
    byte[] read(String key, long maxBytes);

    void putPrivate(String key, byte[] content, String contentType);

    void putPublic(String key, byte[] content, String contentType);

    void delete(String key);

    String publicUrl(String publicKey);
}
