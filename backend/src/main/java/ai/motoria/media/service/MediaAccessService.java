package ai.motoria.media.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.persistence.AuditSubject;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.media.domain.MediaAccessLog;
import ai.motoria.media.domain.MediaObject;
import ai.motoria.media.dto.ReadUrlResponse;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.integration.ObjectStoragePort;
import ai.motoria.media.integration.PresignedUrl;
import ai.motoria.media.repository.MediaAccessLogRepository;
import ai.motoria.media.repository.MediaObjectRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Private reads: short-lived presigned URLs, only for the owner or BACKOFFICE/ADMIN, always written to the access log.
 */
@ApplicationScoped
public class MediaAccessService {

    static final String PURPOSE_READ_URL = "READ_URL";

    @Inject
    MediaObjectRepository mediaObjectRepository;

    @Inject
    MediaAccessLogRepository mediaAccessLogRepository;

    @Inject
    ObjectStoragePort objectStorage;

    @Inject
    CurrentUser currentUser;

    @ConfigProperty(name = "motoria.media.read-url-ttl", defaultValue = "PT5M")
    Duration readUrlTtl;

    @Audited
    @Transactional
    public ReadUrlResponse readUrl(UUID mediaId) {
        MediaObject media = mediaObjectRepository.findByIdOptional(mediaId)
                .orElseThrow(() -> new NotFoundException(MediaErrorCodes.MEDIA_NOT_FOUND, "Media not found"));
        if (!media.isOwnedBy(currentUser.id()) && !currentUser.isBackoffice()) {
            throw new ForbiddenOperationException(MediaErrorCodes.MEDIA_ACCESS_DENIED, "Access denied to this file");
        }
        PresignedUrl presigned = presignAndLog(media, PURPOSE_READ_URL);
        return new ReadUrlResponse(presigned.url(), presigned.expiresAt());
    }

    /**
     * Presigns a private read and records it. Caller must be inside a transaction.
     */
    public PresignedUrl presignAndLog(MediaObject media, String purpose) {
        if (!media.isConfirmed()) {
            throw new InvalidStateException(MediaErrorCodes.MEDIA_INVALID_STATE, "The file is not confirmed");
        }
        PresignedUrl presigned = objectStorage.presignRead(media.getStorageKey(), readUrlTtl);
        logAccess(media.getId(), purpose);
        return presigned;
    }

    public void logAccess(UUID mediaId, String purpose) {
        mediaAccessLogRepository.persist(MediaAccessLog.record(mediaId, AuditSubject.current(), purpose, Instant.now()));
    }
}
