package ai.motoria.media.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.media.exception.MediaErrorCodes;
import jakarta.enterprise.context.ApplicationScoped;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Builds the public copy of a photo without metadata (design D5): the image is decoded, rotated according to the
 * EXIF orientation and re-encoded as JPEG with the JDK. Re-encoding drops every EXIF, XMP, IPTC and comment block
 * (including GPS), so no extra library is needed. Dimensions are checked before decoding to avoid decompression bombs.
 */
@ApplicationScoped
public class ImageSanitizer {

    private static final int MAX_SIDE = 12000;
    private static final long MAX_PIXELS = 60_000_000L;
    private static final float JPEG_QUALITY = 0.9f;

    public DecodedImage decode(byte[] content, String contentType) {
        checkDimensions(content);
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(content));
        } catch (IOException exception) {
            throw unreadable();
        }
        if (image == null) {
            throw unreadable();
        }
        ExifInfo exif = MediaPolicy.JPEG.equals(contentType) ? ExifReader.read(content) : ExifInfo.NONE;
        BufferedImage oriented = applyOrientation(toRgb(image), exif.orientation());
        return new DecodedImage(oriented, exif);
    }

    public byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode the public copy", exception);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        int width = source.getWidth();
        int height = source.getHeight();
        return switch (orientation) {
            case 3 -> transform(source, width, height, Math.PI, width, height);
            case 6 -> transform(source, height, width, Math.PI / 2, height, 0);
            case 8 -> transform(source, height, width, -Math.PI / 2, 0, width);
            default -> source;
        };
    }

    private static BufferedImage transform(BufferedImage source, int newWidth, int newHeight, double angle,
                                           double translateX, double translateY) {
        BufferedImage output = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            AffineTransform transform = new AffineTransform();
            transform.translate(translateX, translateY);
            transform.rotate(angle);
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private void checkDimensions(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw new BusinessRuleException(MediaErrorCodes.MEDIA_IMAGE_TOO_LARGE, "The image dimensions are too large");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw unreadable();
        }
    }

    private BusinessRuleException unreadable() {
        return new BusinessRuleException(MediaErrorCodes.MEDIA_IMAGE_UNREADABLE, "The image could not be read");
    }

    public record DecodedImage(BufferedImage image, ExifInfo exif) {

        public int width() {
            return image.getWidth();
        }

        public int height() {
            return image.getHeight();
        }
    }
}
