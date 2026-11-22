package ai.motoria.ai.integration;

public record LlmResponse(
        String content,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        long latencyMs) {
}
