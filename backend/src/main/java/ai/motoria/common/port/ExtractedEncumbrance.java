package ai.motoria.common.port;

/**
 * @param type PLEDGE, SEIZURE, ANNOTATION, COLLISION or OTHER
 */
public record ExtractedEncumbrance(
        String type,
        String description,
        int page,
        double confidence) {
}
