package ai.motoria.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Error body. {@code code} is stable and upper case; the frontend translates it.
 * {@code details} is optional (for example the missing photo slot codes) and omitted when empty.
 */
public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> details) {

    public ErrorResponse(String code, String message, Instant timestamp) {
        this(code, message, timestamp, List.of());
    }
}
