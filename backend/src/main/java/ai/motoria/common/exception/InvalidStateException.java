package ai.motoria.common.exception;

public class InvalidStateException extends MotoriaException {

    public InvalidStateException(String message) {
        super(409, "INVALID_STATE", message);
    }

    public InvalidStateException(String code, String message) {
        super(409, code, message);
    }
}
