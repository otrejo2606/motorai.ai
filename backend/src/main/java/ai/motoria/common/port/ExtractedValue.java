package ai.motoria.common.port;

/**
 * @param key        attribute key (BRAND, MODEL, YEAR, ..., OWNER_ID_HASH, OWNER_NAME) or DOCUMENT_PLATE / DOCUMENT_DATE
 * @param page       1-based page cited by the model (mandatory)
 * @param confidence 0..1
 */
public record ExtractedValue(
        String key,
        String value,
        int page,
        double confidence) {
}
