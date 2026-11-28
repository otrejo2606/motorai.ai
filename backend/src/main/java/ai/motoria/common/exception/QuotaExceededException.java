package ai.motoria.common.exception;

/**
 * A configured quota was exhausted (HTTP 429).
 */
public class QuotaExceededException extends MotoriaException {

    public QuotaExceededException(String code, String message) {
        super(429, code, message);
    }
}
