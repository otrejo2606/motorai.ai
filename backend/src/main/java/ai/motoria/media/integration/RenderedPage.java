package ai.motoria.media.integration;

public record RenderedPage(
        int pageNumber,
        byte[] png,
        int width,
        int height) {
}
