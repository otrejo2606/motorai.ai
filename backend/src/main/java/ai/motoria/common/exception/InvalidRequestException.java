package ai.motoria.common.exception;

/**
 * The request is syntactically valid but a value is not acceptable (HTTP 400), for example
 * {@code VEHICLE_PLATE_INVALID}.
 */
public class InvalidRequestException extends MotoriaException {

    public InvalidRequestException(String code, String message) {
        super(400, code, message);
    }
}
