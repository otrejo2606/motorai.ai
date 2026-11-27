package ai.motoria.media.service;

import ai.motoria.common.port.PhotoAngleCheckResult;
import ai.motoria.media.domain.PhotoAnglePolicy;
import ai.motoria.media.exception.MediaErrorCodes;
import ai.motoria.media.integration.PhotoAngleClassification;
import ai.motoria.media.integration.PhotoAngleClassifierPort;
import ai.motoria.media.integration.PhotoAngleClassifierUnavailableException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.UUID;

/**
 * Angle check of a listing photo with the local classifier (design D3 and D4 of add-captura-guiada-fotos).
 * Not transactional on purpose: the image is read in the short transaction of {@link PhotoAngleImageReader}, then
 * reduced to motoria.media.angle-check.max-side pixels and re-encoded as JPEG without metadata, and only then sent to
 * the classifier, without any database transaction open. A failure never becomes an error for the seller: the result is
 * UNAVAILABLE with a stable code. Nothing of the image is stored by this service.
 */
@ApplicationScoped
public class PhotoAngleCheckService {

    @Inject
    PhotoAngleImageReader imageReader;

    @Inject
    ImageSanitizer imageSanitizer;

    @Inject
    PhotoAngleClassifierPort classifier;

    @ConfigProperty(name = "motoria.media.angle-check.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "motoria.media.angle-check.slots",
            defaultValue = "FRONT,REAR,LEFT,RIGHT,FRONT_LEFT,FRONT_RIGHT,REAR_LEFT,REAR_RIGHT,INTERIOR_FRONT,INTERIOR_REAR,"
                    + "DASHBOARD_ODOMETER,ENGINE,TIRES,VIN")
    List<String> slots;

    @ConfigProperty(name = "motoria.media.angle-check.accept-threshold", defaultValue = "0.80")
    double acceptThreshold;

    @ConfigProperty(name = "motoria.media.angle-check.reject-threshold", defaultValue = "0.90")
    double rejectThreshold;

    @ConfigProperty(name = "motoria.media.angle-check.max-side", defaultValue = "1024")
    int maxSide;

    public PhotoAngleCheckResult check(UUID mediaId, String expectedSlot) {
        if (!enabled || expectedSlot == null || !slots.contains(expectedSlot)) {
            return PhotoAngleCheckResult.notChecked();
        }
        byte[] image;
        try {
            PhotoAngleImageReader.StoredImage stored = imageReader.read(mediaId);
            image = reduce(stored.content(), stored.contentType());
        } catch (RuntimeException exception) {
            Log.warnv("Angle check of media {0} could not read the image: {1}", mediaId, exception.getClass().getSimpleName());
            return PhotoAngleCheckResult.unavailable(MediaErrorCodes.PHOTO_CLASSIFIER_IMAGE_UNREADABLE, null);
        }

        PhotoAngleClassification classification;
        try {
            classification = classifier.classify(expectedSlot, image, MediaPolicy.JPEG);
        } catch (PhotoAngleClassifierUnavailableException exception) {
            Log.warnv("Angle check of media {0} unavailable: {1}", mediaId, exception.getCode());
            return PhotoAngleCheckResult.unavailable(exception.getCode(), null);
        } catch (RuntimeException exception) {
            Log.warnv("Angle check of media {0} unavailable: {1}", mediaId, exception.getClass().getSimpleName());
            return PhotoAngleCheckResult.unavailable(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE, null);
        }
        return toResult(expectedSlot, classification);
    }

    PhotoAngleCheckResult toResult(String expectedSlot, PhotoAngleClassification classification) {
        String status = PhotoAnglePolicy.decide(expectedSlot, classification.predictedSlot(),
                classification.confidence(), acceptThreshold, rejectThreshold);
        return new PhotoAngleCheckResult(status, classification.predictedSlot(), classification.confidence(),
                classification.modelVersion(), classification.latencyMs(), classification.vin(),
                classification.odometerKm(), null);
    }

    /**
     * Decodes with the same limits as the upload (rotated by its EXIF orientation), reduces the longest side and
     * re-encodes as JPEG, which drops every metadata block (GPS included) before the image leaves the module.
     */
    private byte[] reduce(byte[] content, String contentType) {
        BufferedImage source = imageSanitizer.decode(content, contentType).image();
        int longSide = Math.max(source.getWidth(), source.getHeight());
        if (maxSide <= 0 || longSide <= maxSide) {
            return imageSanitizer.encodeJpeg(source);
        }
        double scale = (double) maxSide / longSide;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage reduced = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = reduced.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return imageSanitizer.encodeJpeg(reduced);
    }
}
