package ai.motoria.media.integration;

/**
 * The classifier is not configured, did not answer in time or answered something unusable. The angle check becomes
 * UNAVAILABLE: it never blocks the seller and the photo goes to the analyst.
 */
public class PhotoAngleClassifierUnavailableException extends RuntimeException {

    private final String code;

    public PhotoAngleClassifierUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PhotoAngleClassifierUnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
