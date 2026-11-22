package ai.motoria.common.exception;

/**
 * A required dependency (storage, model provider, key) is not configured or not reachable (HTTP 503).
 */
public class DependencyUnavailableException extends MotoriaException {

    public DependencyUnavailableException(String code, String message) {
        super(503, code, message);
    }

    public DependencyUnavailableException(String code, String message, Throwable cause) {
        super(503, code, message);
        initCause(cause);
    }
}
