package ai.motoria.media.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.common.security.Roles;
import ai.motoria.media.domain.MediaKind;
import ai.motoria.media.domain.MediaObject;
import ai.motoria.media.domain.MediaStatus;
import ai.motoria.media.dto.CreateUploadRequest;
import ai.motoria.media.dto.MediaResponse;
import ai.motoria.media.dto.UploadTicketResponse;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.integration.ObjectStoragePort;
import ai.motoria.media.integration.PresignedUrl;
import ai.motoria.media.mapper.MediaMapper;
import ai.motoria.media.repository.MediaObjectRepository;
import ai.motoria.media.repository.PhotoProtocolSlotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Two-step upload (design D5): a presigned PUT with fixed type and size, then a confirmation that reads the
 * object back and checks size, content signature and sha256. Rejected objects are deleted.
 */
@ApplicationScoped
public class MediaUploadService {

    @Inject
    MediaObjectRepository mediaObjectRepository;

    @Inject
    PhotoProtocolSlotRepository photoProtocolSlotRepository;

    @Inject
    ObjectStoragePort objectStorage;

    @Inject
    MediaPolicy mediaPolicy;

    @Inject
    ContentSignatureVerifier contentSignatureVerifier;

    @Inject
    ImageSanitizer imageSanitizer;

    @Inject
    MediaMapper mediaMapper;

    @Inject
    CurrentUser currentUser;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "motoria.media.upload-url-ttl", defaultValue = "PT10M")
    Duration uploadUrlTtl;

    @ConfigProperty(name = "motoria.media.default-country", defaultValue = "CR")
    String defaultCountry;

    @Audited
    @Transactional
    public UploadTicketResponse requestUpload(CreateUploadRequest request) {
        UUID ownerId = currentUser.id();
        MediaKind kind = request.kind();
        assertKindAllowedForCaller(kind);
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        if (!mediaPolicy.allowedContentTypes(kind).contains(contentType)) {
            throw new InvalidRequestException(MediaErrorCodes.MEDIA_TYPE_NOT_ALLOWED, "This file type is not allowed");
        }
        if (request.sizeBytes() > mediaPolicy.maxSize(kind)) {
            throw new InvalidRequestException(MediaErrorCodes.MEDIA_TOO_LARGE, "The file is larger than the allowed size");
        }
        String photoSlot = resolvePhotoSlot(kind, request.photoSlot());

        UUID mediaId = UUID.randomUUID();
        String storageKey = kind.name().toLowerCase(Locale.ROOT) + "/" + ownerId + "/" + mediaId;
        PresignedUrl presigned = objectStorage.presignUpload(storageKey, contentType, request.sizeBytes(), uploadUrlTtl);
        mediaObjectRepository.persist(MediaObject.pendingUpload(
                mediaId, ownerId, kind, photoSlot, storageKey, contentType, request.sizeBytes()));
        return new UploadTicketResponse(mediaId, presigned.url(), presigned.expiresAt());
    }

    /**
     * Rejections are committed (status REJECTED, object deleted) before the error is returned.
     */
    @Audited
    @Transactional(dontRollbackOn = BusinessRuleException.class)
    public MediaResponse confirm(UUID mediaId) {
        MediaObject media = mediaObjectRepository.findByIdOptional(mediaId)
                .filter(candidate -> candidate.isOwnedBy(currentUser.id()))
                .orElseThrow(() -> new NotFoundException(MediaErrorCodes.MEDIA_NOT_FOUND, "Media not found"));
        if (media.isConfirmed()) {
            return mediaMapper.toResponse(media, publicUrl(media));
        }
        if (media.getStatus() == MediaStatus.REJECTED) {
            throw new InvalidStateException(MediaErrorCodes.MEDIA_INVALID_STATE, "The file was rejected; upload it again");
        }

        long maxSize = mediaPolicy.maxSize(media.getKind());
        long size = objectStorage.objectSize(media.getStorageKey())
                .orElseThrow(() -> new InvalidStateException(MediaErrorCodes.MEDIA_NOT_UPLOADED, "The file was not uploaded yet"));
        if (size > maxSize) {
            throw reject(media, MediaErrorCodes.MEDIA_TOO_LARGE, "The file is larger than the allowed size");
        }
        if (size != media.getDeclaredSize()) {
            throw reject(media, MediaErrorCodes.MEDIA_SIZE_MISMATCH, "The uploaded size does not match the declared size");
        }
        byte[] content = objectStorage.read(media.getStorageKey(), maxSize);
        if (!contentSignatureVerifier.matches(media.getContentType(), content)) {
            throw reject(media, MediaErrorCodes.MEDIA_SIGNATURE_MISMATCH, "The file content does not match its type");
        }

        Integer width = null;
        Integer height = null;
        String captureMetadata = null;
        if (mediaPolicy.isDecodableImage(media.getContentType())) {
            ImageSanitizer.DecodedImage decoded;
            try {
                decoded = imageSanitizer.decode(content, media.getContentType());
            } catch (BusinessRuleException exception) {
                throw reject(media, exception.getCode(), exception.getMessage());
            }
            width = decoded.width();
            height = decoded.height();
            captureMetadata = captureMetadata(decoded.exif());
            if (media.getKind() == MediaKind.LISTING_PHOTO) {
                if (!mediaPolicy.meetsMinimumResolution(width, height)) {
                    throw reject(media, MediaErrorCodes.MEDIA_RESOLUTION_TOO_LOW, "The photo resolution is too low");
                }
                String publicKey = "listing-photos/" + media.getId() + ".jpg";
                objectStorage.putPublic(publicKey, imageSanitizer.encodeJpeg(decoded.image()), MediaPolicy.JPEG);
                media.attachPublicCopy(publicKey);
            }
        }
        media.confirm(size, sha256(content), width, height, captureMetadata, Instant.now());
        return mediaMapper.toResponse(media, publicUrl(media));
    }

    private BusinessRuleException reject(MediaObject media, String code, String message) {
        objectStorage.delete(media.getStorageKey());
        media.reject(code);
        return new BusinessRuleException(code, message);
    }

    private void assertKindAllowedForCaller(MediaKind kind) {
        boolean allowed = switch (kind) {
            case IDENTITY_DOCUMENT -> currentUser.isAuthenticated();
            case LISTING_PHOTO -> currentUser.hasRole(Roles.SELLER) || currentUser.hasRole(Roles.DEALER)
                    || currentUser.hasRole(Roles.ADMIN);
            case REGISTRY_DOCUMENT -> currentUser.hasRole(Roles.SELLER) || currentUser.hasRole(Roles.DEALER)
                    || currentUser.isBackoffice();
            case REGISTRY_PAGE_IMAGE -> false;
        };
        if (!allowed) {
            throw new ForbiddenOperationException(MediaErrorCodes.MEDIA_KIND_NOT_ALLOWED, "This kind of file cannot be uploaded");
        }
    }

    private String resolvePhotoSlot(MediaKind kind, String photoSlot) {
        if (kind != MediaKind.LISTING_PHOTO) {
            return null;
        }
        if (photoSlot == null || photoSlot.isBlank()) {
            throw new InvalidRequestException(MediaErrorCodes.MEDIA_PHOTO_SLOT_REQUIRED, "The photo slot is required");
        }
        if (photoProtocolSlotRepository.findByCountryAndCode(defaultCountry, photoSlot).isEmpty()) {
            throw new InvalidRequestException(MediaErrorCodes.MEDIA_PHOTO_SLOT_UNKNOWN, "Unknown photo slot");
        }
        return photoSlot;
    }

    private String captureMetadata(ExifInfo exif) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("exif", exif.present());
        node.put("gps", exif.gps());
        node.put("orientation", exif.orientation());
        return node.toString();
    }

    private String publicUrl(MediaObject media) {
        return media.getPublicKey() == null ? null : objectStorage.publicUrl(media.getPublicKey());
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
