package ai.motoria.ai.exception;

/**
 * The model output is not valid JSON or does not follow the expected schema.
 */
public class InvalidModelOutputException extends RuntimeException {

    public InvalidModelOutputException(String message) {
        super(message);
    }

    public InvalidModelOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
