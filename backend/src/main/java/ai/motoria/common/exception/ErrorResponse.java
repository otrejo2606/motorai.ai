package ai.motoria.common.exception;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp) {
}
