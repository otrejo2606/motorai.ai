package ai.motoria.media.service;

import ai.motoria.common.exception.NotFoundException;
import ai.motoria.media.domain.MediaKind;
import ai.motoria.media.domain.MediaObject;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.integration.ObjectStoragePort;
import ai.motoria.media.repository.MediaObjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Short transaction that reads the private original of a confirmed listing photo for the angle check and writes the
 * read to the media access log. Decoding and the classifier call happen afterwards, outside the transaction.
 */
@ApplicationScoped
public class PhotoAngleImageReader {

    static final String PURPOSE = "PHOTO_ANGLE_CHECK";

    @Inject
    MediaObjectRepository mediaObjectRepository;

    @Inject
    ObjectStoragePort objectStorage;

    @Inject
    MediaPolicy mediaPolicy;

    @Inject
    MediaAccessService mediaAccessService;

    public record StoredImage(byte[] content, String contentType) {
    }

    @Transactional
    public StoredImage read(UUID mediaId) {
        MediaObject media = mediaObjectRepository.findByIdOptional(mediaId)
                .filter(MediaObject::isConfirmed)
                .filter(candidate -> candidate.getKind() == MediaKind.LISTING_PHOTO)
                .orElseThrow(() -> new NotFoundException(MediaErrorCodes.MEDIA_NOT_FOUND, "Listing photo not found"));
        byte[] content = objectStorage.read(media.getStorageKey(), mediaPolicy.maxSize(media.getKind()));
        mediaAccessService.logAccess(media.getId(), PURPOSE);
        return new StoredImage(content, media.getContentType());
    }
}
