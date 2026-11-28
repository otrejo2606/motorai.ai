package ai.motoria.media.exception;

/**
 * Stable error codes of the media module (the frontend translates them).
 */
public final class MediaErrorCodes {

    public static final String MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND";
    public static final String MEDIA_ACCESS_DENIED = "MEDIA_ACCESS_DENIED";
    public static final String MEDIA_KIND_NOT_ALLOWED = "MEDIA_KIND_NOT_ALLOWED";
    public static final String MEDIA_TYPE_NOT_ALLOWED = "MEDIA_TYPE_NOT_ALLOWED";
    public static final String MEDIA_TOO_LARGE = "MEDIA_TOO_LARGE";
    public static final String MEDIA_PHOTO_SLOT_REQUIRED = "MEDIA_PHOTO_SLOT_REQUIRED";
    public static final String MEDIA_PHOTO_SLOT_UNKNOWN = "MEDIA_PHOTO_SLOT_UNKNOWN";
    public static final String MEDIA_INVALID_STATE = "MEDIA_INVALID_STATE";
    public static final String MEDIA_NOT_UPLOADED = "MEDIA_NOT_UPLOADED";
    public static final String MEDIA_SIZE_MISMATCH = "MEDIA_SIZE_MISMATCH";
    public static final String MEDIA_SIGNATURE_MISMATCH = "MEDIA_SIGNATURE_MISMATCH";
    public static final String MEDIA_IMAGE_UNREADABLE = "MEDIA_IMAGE_UNREADABLE";
    public static final String MEDIA_IMAGE_TOO_LARGE = "MEDIA_IMAGE_TOO_LARGE";
    public static final String MEDIA_RESOLUTION_TOO_LOW = "MEDIA_RESOLUTION_TOO_LOW";
    public static final String MEDIA_PDF_UNREADABLE = "MEDIA_PDF_UNREADABLE";
    public static final String MEDIA_NOT_A_PDF = "MEDIA_NOT_A_PDF";
    public static final String PHOTO_PROTOCOL_INVALID = "PHOTO_PROTOCOL_INVALID";
    public static final String PHOTO_PROTOCOL_CONFLICT = "PHOTO_PROTOCOL_CONFLICT";
    public static final String MEDIA_PAGES_IN_PROGRESS = "MEDIA_PAGES_IN_PROGRESS";
    public static final String STORAGE_NOT_CONFIGURED = "STORAGE_NOT_CONFIGURED";
    public static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";
    /** Angle classifier (add-captura-guiada-fotos): stored as the reason of an UNAVAILABLE check, never an HTTP error. */
    public static final String PHOTO_CLASSIFIER_NOT_CONFIGURED = "PHOTO_CLASSIFIER_NOT_CONFIGURED";
    public static final String PHOTO_CLASSIFIER_UNAVAILABLE = "PHOTO_CLASSIFIER_UNAVAILABLE";
    public static final String PHOTO_CLASSIFIER_INVALID_RESPONSE = "PHOTO_CLASSIFIER_INVALID_RESPONSE";
    public static final String PHOTO_CLASSIFIER_IMAGE_UNREADABLE = "PHOTO_CLASSIFIER_IMAGE_UNREADABLE";

    private MediaErrorCodes() {
    }
}
