package ai.motoria.common.exception;

public class NotFoundException extends MotoriaException {

    public NotFoundException(String message) {
        super(404, "NOT_FOUND", message);
    }

    public NotFoundException(String code, String message) {
        super(404, code, message);
    }
}
