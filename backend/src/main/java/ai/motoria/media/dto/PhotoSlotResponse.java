package ai.motoria.media.dto;

public record PhotoSlotResponse(
        String code,
        boolean required,
        int order,
        String instructionKey) {
}
