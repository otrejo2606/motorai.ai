package ai.motoria.media.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.common.security.Roles;
import ai.motoria.media.domain.MediaKind;
import ai.motoria.media.domain.MediaObject;
import ai.motoria.media.domain.MediaStatus;
import ai.motoria.media.domain.MediaVisibility;
import ai.motoria.media.dto.CreateUploadRequest;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.integration.ObjectStoragePort;
import ai.motoria.media.mapper.MediaMapper;
import ai.motoria.media.repository.MediaObjectRepository;
import ai.motoria.media.repository.PhotoProtocolSlotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Upload security (design D5): fake content signature, size limits and the metadata-free public copy.
 */
class MediaUploadServiceTest {

    private final MediaObjectRepository mediaObjectRepository = mock(MediaObjectRepository.class);
    private final PhotoProtocolSlotRepository photoProtocolSlotRepository = mock(PhotoProtocolSlotRepository.class);
    private final ObjectStoragePort objectStorage = mock(ObjectStoragePort.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UUID ownerId = UUID.randomUUID();
    private MediaUploadService service;

    @BeforeEach
    void setUp() {
        MediaPolicy policy = new MediaPolicy();
        policy.maxRegistryDocument = 1024;
        policy.maxIdentityDocument = 1024;
        policy.maxListingPhoto = 5 * 1024 * 1024;
        policy.minPhotoWidth = 1024;
        policy.minPhotoHeight = 768;

        service = new MediaUploadService();
        service.mediaObjectRepository = mediaObjectRepository;
        service.photoProtocolSlotRepository = photoProtocolSlotRepository;
        service.objectStorage = objectStorage;
        service.mediaPolicy = policy;
        service.contentSignatureVerifier = new ContentSignatureVerifier();
        service.imageSanitizer = new ImageSanitizer();
        service.mediaMapper = mock(MediaMapper.class);
        service.currentUser = currentUser;
        service.objectMapper = new ObjectMapper();
        service.uploadUrlTtl = Duration.ofMinutes(10);
        service.defaultCountry = "CR";

        when(currentUser.id()).thenReturn(ownerId);
        when(currentUser.isAuthenticated()).thenReturn(true);
        when(currentUser.hasRole(Roles.SELLER)).thenReturn(true);
        when(objectStorage.publicUrl(anyString())).thenReturn("https://cdn.example/photo.jpg");
    }

    @Test
    void requestShouldRejectSizeAboveLimit() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () -> service.requestUpload(
                new CreateUploadRequest(MediaKind.REGISTRY_DOCUMENT, "application/pdf", 4096L, null)));

        assertEquals(MediaErrorCodes.MEDIA_TOO_LARGE, exception.getCode());
        verify(objectStorage, never()).presignUpload(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void requestShouldRejectTypeOutsideWhitelist() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () -> service.requestUpload(
                new CreateUploadRequest(MediaKind.REGISTRY_DOCUMENT, "application/x-msdownload", 100L, null)));

        assertEquals(MediaErrorCodes.MEDIA_TYPE_NOT_ALLOWED, exception.getCode());
    }

    @Test
    void confirmShouldRejectExecutableRenamedAsPdfAndDeleteIt() {
        byte[] executable = "MZ-this-is-not-a-pdf".getBytes(StandardCharsets.US_ASCII);
        MediaObject media = pending(MediaKind.REGISTRY_DOCUMENT, "application/pdf", executable.length);
        when(objectStorage.objectSize(media.getStorageKey())).thenReturn(Optional.of((long) executable.length));
        when(objectStorage.read(eq(media.getStorageKey()), anyLong())).thenReturn(executable);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.confirm(media.getId()));

        assertEquals(MediaErrorCodes.MEDIA_SIGNATURE_MISMATCH, exception.getCode());
        assertEquals(MediaStatus.REJECTED, media.getStatus());
        verify(objectStorage).delete(media.getStorageKey());
    }

    @Test
    void confirmShouldRejectUploadedSizeDifferentFromDeclared() {
        MediaObject media = pending(MediaKind.REGISTRY_DOCUMENT, "application/pdf", 10);
        when(objectStorage.objectSize(media.getStorageKey())).thenReturn(Optional.of(900L));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.confirm(media.getId()));

        assertEquals(MediaErrorCodes.MEDIA_SIZE_MISMATCH, exception.getCode());
        verify(objectStorage).delete(media.getStorageKey());
    }

    @Test
    void confirmShouldRejectObjectAboveLimit() {
        MediaObject media = pending(MediaKind.REGISTRY_DOCUMENT, "application/pdf", 10);
        when(objectStorage.objectSize(media.getStorageKey())).thenReturn(Optional.of(2048L));

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.confirm(media.getId()));

        assertEquals(MediaErrorCodes.MEDIA_TOO_LARGE, exception.getCode());
    }

    @Test
    void confirmShouldAcceptRealPdfAndComputeSha256() {
        byte[] pdf = "%PDF-1.7 minimal".getBytes(StandardCharsets.US_ASCII);
        MediaObject media = pending(MediaKind.REGISTRY_DOCUMENT, "application/pdf", pdf.length);
        when(objectStorage.objectSize(media.getStorageKey())).thenReturn(Optional.of((long) pdf.length));
        when(objectStorage.read(eq(media.getStorageKey()), anyLong())).thenReturn(pdf);

        service.confirm(media.getId());

        assertEquals(MediaStatus.CONFIRMED, media.getStatus());
        assertEquals(64, media.getSha256().length());
        verify(objectStorage, never()).delete(anyString());
    }

    @Test
    void lowResolutionPhotoShouldBeRejected() throws IOException {
        byte[] png = png(320, 240);
        MediaObject media = pending(MediaKind.LISTING_PHOTO, "image/png", png.length);
        when(objectStorage.objectSize(media.getStorageKey())).thenReturn(Optional.of((long) png.length));
        when(objectStorage.read(eq(media.getStorageKey()), anyLong())).thenReturn(png);

        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> service.confirm(media.getId()));

        assertEquals(MediaErrorCodes.MEDIA_RESOLUTION_TOO_LOW, exception.getCode());
    }

    @Test
    void validPhotoShouldGetAPublicCopyWithoutMetadata() throws IOException {
        byte[] png = png(1280, 960);
        MediaObject media = pending(MediaKind.LISTING_PHOTO, "image/png", png.length);
        when(objectStorage.objectSize(media.getStorageKey())).thenReturn(Optional.of((long) png.length));
        when(objectStorage.read(eq(media.getStorageKey()), anyLong())).thenReturn(png);

        service.confirm(media.getId());

        ArgumentCaptor<byte[]> publicCopy = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorage).putPublic(anyString(), publicCopy.capture(), eq("image/jpeg"));
        assertFalse(ExifReader.read(publicCopy.getValue()).present());
        assertEquals(MediaVisibility.PUBLIC, media.getVisibility());
        assertNotNull(media.getPublicKey());
        assertEquals(1280, media.getWidth());
    }

    private MediaObject pending(MediaKind kind, String contentType, long declaredSize) {
        MediaObject media = MediaObject.pendingUpload(UUID.randomUUID(), ownerId, kind,
                kind == MediaKind.LISTING_PHOTO ? "FRONT" : null, "key/" + UUID.randomUUID(), contentType, declaredSize);
        when(mediaObjectRepository.findByIdOptional(media.getId())).thenReturn(Optional.of(media));
        return media;
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
