package ai.motoria.media.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.MediaReadUrl;
import ai.motoria.common.port.MediaSnapshot;
import ai.motoria.common.port.PhotoSlotSnapshot;
import ai.motoria.media.domain.MediaKind;
import ai.motoria.media.domain.MediaObject;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.integration.ObjectStoragePort;
import ai.motoria.media.integration.PdfPageRenderer;
import ai.motoria.media.integration.PresignedUrl;
import ai.motoria.media.integration.RenderedPage;
import ai.motoria.media.mapper.MediaMapper;
import ai.motoria.media.repository.MediaObjectRepository;
import ai.motoria.media.repository.PhotoProtocolSlotRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the shared {@link MediaPort} owned by the media module.
 */
@ApplicationScoped
public class MediaPortAdapter implements MediaPort {

    @Inject
    MediaObjectRepository mediaObjectRepository;

    @Inject
    PhotoProtocolSlotRepository photoProtocolSlotRepository;

    @Inject
    ObjectStoragePort objectStorage;

    @Inject
    PdfPageRenderer pdfPageRenderer;

    @Inject
    MediaAccessService mediaAccessService;

    @Inject
    MediaPolicy mediaPolicy;

    @Inject
    MediaMapper mediaMapper;

    @ConfigProperty(name = "motoria.media.pdf.max-pages", defaultValue = "10")
    int maxPdfPages;

    @ConfigProperty(name = "motoria.media.pdf.render-dpi", defaultValue = "110")
    float renderDpi;

    @Override
    public Optional<MediaSnapshot> findMedia(UUID mediaId) {
        return mediaObjectRepository.findByIdOptional(mediaId)
                .map(media -> mediaMapper.toSnapshot(media,
                        media.getPublicKey() == null ? null : objectStorage.publicUrl(media.getPublicKey())));
    }

    @Override
    public List<PhotoSlotSnapshot> photoProtocol(String countryCode) {
        return photoProtocolSlotRepository.listByCountry(countryCode).stream()
                .map(mediaMapper::toSnapshot)
                .toList();
    }

    @Override
    @Transactional
    public byte[] readContent(UUID mediaId, String purpose) {
        MediaObject media = findConfirmed(mediaId);
        byte[] content = objectStorage.read(media.getStorageKey(), mediaPolicy.maxSize(media.getKind()));
        mediaAccessService.logAccess(media.getId(), purpose);
        return content;
    }

    /**
     * Own transaction: a failure while rendering never marks the caller's transaction (for example the analyst's
     * review detail) for rollback, and the access log of the successful part is kept.
     */
    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<UUID> ensurePdfPageImages(UUID pdfMediaId) {
        List<MediaObject> existing = mediaObjectRepository.listPageImages(pdfMediaId);
        if (!existing.isEmpty()) {
            return existing.stream().map(MediaObject::getId).toList();
        }
        MediaObject source = findConfirmed(pdfMediaId);
        if (source.getKind() != MediaKind.REGISTRY_DOCUMENT || !MediaPolicy.PDF.equals(source.getContentType())) {
            throw new BusinessRuleException(MediaErrorCodes.MEDIA_NOT_A_PDF, "The file is not a registry PDF");
        }
        byte[] pdf = objectStorage.read(source.getStorageKey(), mediaPolicy.maxSize(source.getKind()));
        mediaAccessService.logAccess(source.getId(), "RENDER_PAGES");
        List<UUID> pageIds = new ArrayList<>();
        Instant now = Instant.now();
        for (RenderedPage page : pdfPageRenderer.render(pdf, maxPdfPages, renderDpi)) {
            String key = "registry-pages/" + source.getId() + "/" + page.pageNumber() + ".png";
            objectStorage.putPrivate(key, page.png(), MediaPolicy.PNG);
            MediaObject pageImage = MediaObject.pageImage(source, page.pageNumber(), key, page.png().length,
                    MediaUploadService.sha256(page.png()), page.width(), page.height(), now);
            mediaObjectRepository.persist(pageImage);
            pageIds.add(pageImage.getId());
        }
        try {
            mediaObjectRepository.flush();
        } catch (PersistenceException exception) {
            if (UniqueViolations.isUniqueViolation(exception)) {
                throw new InvalidStateException(MediaErrorCodes.MEDIA_PAGES_IN_PROGRESS,
                        "The document pages are being prepared; try again");
            }
            throw exception;
        }
        return pageIds;
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public MediaReadUrl createReadUrl(UUID mediaId, String purpose) {
        MediaObject media = findConfirmed(mediaId);
        PresignedUrl presigned = mediaAccessService.presignAndLog(media, purpose);
        return new MediaReadUrl(media.getId(), presigned.url(), presigned.expiresAt());
    }

    private MediaObject findConfirmed(UUID mediaId) {
        return mediaObjectRepository.findByIdOptional(mediaId)
                .filter(MediaObject::isConfirmed)
                .orElseThrow(() -> new NotFoundException(MediaErrorCodes.MEDIA_NOT_FOUND, "Media not found"));
    }
}
