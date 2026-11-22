package ai.motoria.media.service;

/**
 * Minimal EXIF facts read from a JPEG: whether EXIF exists, its orientation and whether GPS data is present.
 */
public record ExifInfo(
        boolean present,
        int orientation,
        boolean gps) {

    public static final ExifInfo NONE = new ExifInfo(false, 1, false);
}
