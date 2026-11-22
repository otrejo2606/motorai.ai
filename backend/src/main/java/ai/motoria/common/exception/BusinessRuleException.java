package ai.motoria.common.exception;

import java.util.List;

/**
 * A business rule rejected the request (HTTP 422).
 */
public class BusinessRuleException extends MotoriaException {

    public BusinessRuleException(String code, String message) {
        super(422, code, message);
    }

    public BusinessRuleException(String code, String message, List<String> details) {
        super(422, code, message, details);
    }
}
