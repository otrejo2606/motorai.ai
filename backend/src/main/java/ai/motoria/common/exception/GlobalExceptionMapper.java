package ai.motoria.common.exception;

import io.quarkus.logging.Log;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.List;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof MotoriaException motoriaException) {
            return Response.status(motoriaException.getStatus())
                    .entity(new ErrorResponse(
                            motoriaException.getCode(),
                            motoriaException.getMessage(),
                            Instant.now(),
                            motoriaException.getDetails()))
                    .build();
        }
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            return buildValidationError(constraintViolationException);
        }
        Log.error("Unhandled exception", exception);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.", Instant.now()))
                .build();
    }

    private Response buildValidationError(ConstraintViolationException exception) {
        List<ValidationErrorDetail> violations = exception.getConstraintViolations().stream()
                .map(violation -> new ValidationErrorDetail(extractLeafField(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ValidationErrorResponse(
                        "VALIDATION_ERROR",
                        "Request validation failed.",
                        Instant.now(),
                        violations))
                .build();
    }

    private String extractLeafField(String path) {
        int lastSeparator = path.lastIndexOf('.');
        return lastSeparator >= 0 ? path.substring(lastSeparator + 1) : path;
    }
}
