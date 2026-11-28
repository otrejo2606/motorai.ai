package ai.motoria.common.port;

public record PhotoSlotSnapshot(
        String code,
        boolean required,
        int order,
        String instructionKey) {
}
