package ai.motoria.ai.exception;

/**
 * The model provider could not be called or returned an error.
 */
public class LlmUnavailableException extends RuntimeException {

    private final String code;

    public LlmUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public LlmUnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
