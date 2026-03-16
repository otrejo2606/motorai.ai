package ai.motoria.common.exception;

public record ValidationErrorDetail(
        String field,
        String message) {
}