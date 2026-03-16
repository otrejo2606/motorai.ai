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
        if (exception instanceof NotFoundException notFoundException) {
            return build(Response.Status.NOT_FOUND, "NOT_FOUND", notFoundException.getMessage());
        }
        if (exception instanceof InvalidStateException invalidStateException) {
            return build(Response.Status.CONFLICT, "INVALID_STATE", invalidStateException.getMessage());
        }
        if (exception instanceof ForbiddenOperationException forbiddenOperationException) {
            return build(Response.Status.FORBIDDEN, "FORBIDDEN", forbiddenOperationException.getMessage());
        }
        if (exception instanceof ConstraintViolationException constraintViolationException) {
            return buildValidationError(constraintViolationException);
        }
        Log.error("Unhandled exception", exception);
        return build(Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    private Response build(Response.Status status, String code, String message) {
        return Response.status(status)
                .entity(new ErrorResponse(code, message, Instant.now()))
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