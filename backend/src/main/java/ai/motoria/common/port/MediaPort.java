package ai.motoria.common.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by {@code media}. Callers authorize the business action; every content read and
 * every read URL issued through this port is written to the media access log with its purpose.
 */
public interface MediaPort {

    Optional<MediaSnapshot> findMedia(UUID mediaId);

    List<PhotoSlotSnapshot> photoProtocol(String countryCode);

    byte[] readContent(UUID mediaId, String purpose);

    /**
     * Renders the pages of a confirmed PDF as private PNG media objects (idempotent) and returns their ids in page order.
     */
    List<UUID> ensurePdfPageImages(UUID pdfMediaId);

    MediaReadUrl createReadUrl(UUID mediaId, String purpose);
}
