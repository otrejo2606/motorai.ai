package ai.motoria.common.exception;

import java.util.List;

/**
 * Base runtime exception carrying a stable, upper-case error code that the frontend translates,
 * the HTTP status to return and optional details (for example the missing photo slots).
 * Messages must never contain personal data.
 */
public class MotoriaException extends RuntimeException {

    private final int status;
    private final String code;
    private final List<String> details;

    public MotoriaException(int status, String code, String message) {
        this(status, code, message, List.of());
    }

    public MotoriaException(int status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }
}
