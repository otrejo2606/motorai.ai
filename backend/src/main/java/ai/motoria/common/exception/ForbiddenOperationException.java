package ai.motoria.common.exception;

public class ForbiddenOperationException extends MotoriaException {

    public ForbiddenOperationException(String message) {
        super(403, "FORBIDDEN", message);
    }

    public ForbiddenOperationException(String code, String message) {
        super(403, code, message);
    }
}
