package ai.motoria.common.exception;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.List;

@Provider
@Priority(Priorities.USER - 10)
public class ResteasyReactiveViolationExceptionMapper implements ExceptionMapper<ResteasyReactiveViolationException> {

    @Override
    public Response toResponse(ResteasyReactiveViolationException exception) {
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
