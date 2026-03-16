package ai.motoria.common.exception;

import java.time.Instant;
import java.util.List;

public record ValidationErrorResponse(
        String code,
        String message,
        Instant timestamp,
        List<ValidationErrorDetail> violations) {
}