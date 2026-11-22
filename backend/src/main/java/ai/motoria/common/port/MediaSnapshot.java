package ai.motoria.common.port;

import java.util.UUID;

/**
 * @param kind   REGISTRY_DOCUMENT, LISTING_PHOTO, IDENTITY_DOCUMENT or REGISTRY_PAGE_IMAGE
 * @param status PENDING_UPLOAD, CONFIRMED or REJECTED
 */
public record MediaSnapshot(
        UUID id,
        UUID ownerId,
        String kind,
        String photoSlot,
        String status,
        String contentType,
        String publicUrl,
        String sha256) {

    public static final String STATUS_CONFIRMED = "CONFIRMED";

    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equals(status);
    }
}
