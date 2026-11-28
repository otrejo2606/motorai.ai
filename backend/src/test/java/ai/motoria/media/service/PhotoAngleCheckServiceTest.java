package ai.motoria.media.service;

import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.port.PhotoAngleCheckResult;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.integration.PhotoAngleClassification;
import ai.motoria.media.integration.PhotoAngleClassifierPort;
import ai.motoria.media.integration.PhotoAngleClassifierUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Angle check orchestration in the media module (design D3 and D4 of add-captura-guiada-fotos).
 */
class PhotoAngleCheckServiceTest {

    private final PhotoAngleImageReader imageReader = mock(PhotoAngleImageReader.class);
    private final PhotoAngleClassifierPort classifier = mock(PhotoAngleClassifierPort.class);
    private final ImageSanitizer imageSanitizer = new ImageSanitizer();
    private final PhotoAngleCheckService service = new PhotoAngleCheckService();
    private final UUID mediaId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service.imageReader = imageReader;
        service.classifier = classifier;
        service.imageSanitizer = imageSanitizer;
        service.enabled = true;
        service.slots = List.of("FRONT", "VIN", "DASHBOARD_ODOMETER");
        service.acceptThreshold = 0.80;
        service.rejectThreshold = 0.90;
        service.maxSide = 64;
    }

    @Test
    void disabledCheckOrSlotOutsideTheListShouldNotCallTheClassifier() {
        assertEquals(PhotoAngleCheckResult.NOT_CHECKED, service.check(mediaId, "ENGINE").status());
        service.enabled = false;
        assertEquals(PhotoAngleCheckResult.NOT_CHECKED, service.check(mediaId, "FRONT").status());

        verifyNoInteractions(imageReader, classifier);
    }

    @Test
    void imageShouldBeReducedAndSentAsJpeg() {
        when(imageReader.read(mediaId)).thenReturn(new PhotoAngleImageReader.StoredImage(jpeg(200, 100), "image/jpeg"));
        when(classifier.classify(eq("FRONT"), any(), eq("image/jpeg")))
                .thenReturn(new PhotoAngleClassification("FRONT", 0.93, "angle-cls-1", null, null, 30L));

        PhotoAngleCheckResult result = service.check(mediaId, "FRONT");

        ArgumentCaptor<byte[]> sent = ArgumentCaptor.forClass(byte[].class);
        verify(classifier).classify(eq("FRONT"), sent.capture(), eq("image/jpeg"));
        BufferedImage reduced = imageSanitizer.decode(sent.getValue(), "image/jpeg").image();
        assertEquals(64, reduced.getWidth());
        assertEquals(32, reduced.getHeight());
        assertEquals(PhotoAngleCheckResult.MATCH, result.status());
        assertEquals("angle-cls-1", result.modelVersion());
        assertEquals(30L, result.latencyMs());
        assertNull(result.errorCode());
    }

    @Test
    void anotherSlotWithHighConfidenceShouldBeMismatchWithTheDetectedSlot() {
        when(imageReader.read(mediaId)).thenReturn(new PhotoAngleImageReader.StoredImage(jpeg(40, 30), "image/jpeg"));
        when(classifier.classify(anyString(), any(), anyString()))
                .thenReturn(new PhotoAngleClassification("REAR", 0.97, "angle-cls-1", null, null, 12L));

        PhotoAngleCheckResult result = service.check(mediaId, "FRONT");

        assertEquals(PhotoAngleCheckResult.MISMATCH, result.status());
        assertEquals("REAR", result.predictedSlot());
    }

    @Test
    void readingsShouldTravelWithTheResult() {
        when(imageReader.read(mediaId)).thenReturn(new PhotoAngleImageReader.StoredImage(jpeg(40, 30), "image/jpeg"));
        when(classifier.classify(anyString(), any(), anyString()))
                .thenReturn(new PhotoAngleClassification("DASHBOARD_ODOMETER", 0.91, "angle-ocr-1", null, 45210, 80L));

        PhotoAngleCheckResult result = service.check(mediaId, "DASHBOARD_ODOMETER");

        assertEquals(PhotoAngleCheckResult.MATCH, result.status());
        assertEquals(45210, result.odometerKm());
    }

    @Test
    void classifierFailureShouldBeUnavailableWithItsCode() {
        when(imageReader.read(mediaId)).thenReturn(new PhotoAngleImageReader.StoredImage(jpeg(40, 30), "image/jpeg"));
        when(classifier.classify(anyString(), any(), anyString()))
                .thenThrow(new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE, "timeout"));

        PhotoAngleCheckResult result = service.check(mediaId, "VIN");

        assertEquals(PhotoAngleCheckResult.UNAVAILABLE, result.status());
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE, result.errorCode());
    }

    @Test
    void unexpectedClassifierErrorShouldAlsoBeUnavailable() {
        when(imageReader.read(mediaId)).thenReturn(new PhotoAngleImageReader.StoredImage(jpeg(40, 30), "image/jpeg"));
        when(classifier.classify(anyString(), any(), anyString())).thenThrow(new IllegalStateException("boom"));

        PhotoAngleCheckResult result = service.check(mediaId, "VIN");

        assertEquals(PhotoAngleCheckResult.UNAVAILABLE, result.status());
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE, result.errorCode());
    }

    @Test
    void unreadableImageShouldBeUnavailableWithoutCallingTheClassifier() {
        when(imageReader.read(mediaId)).thenReturn(new PhotoAngleImageReader.StoredImage(new byte[] {1, 2, 3}, "image/jpeg"));

        PhotoAngleCheckResult result = service.check(mediaId, "FRONT");

        assertEquals(PhotoAngleCheckResult.UNAVAILABLE, result.status());
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_IMAGE_UNREADABLE, result.errorCode());
        verifyNoInteractions(classifier);
    }

    @Test
    void missingMediaShouldBeUnavailable() {
        when(imageReader.read(mediaId)).thenThrow(new NotFoundException(MediaErrorCodes.MEDIA_NOT_FOUND, "Listing photo not found"));

        PhotoAngleCheckResult result = service.check(mediaId, "FRONT");

        assertTrue(PhotoAngleCheckResult.UNAVAILABLE.equals(result.status()));
        verifyNoInteractions(classifier);
    }

    private byte[] jpeg(int width, int height) {
        return imageSanitizer.encodeJpeg(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
    }
}
