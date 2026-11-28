package ai.motoria.common.port;

import java.util.UUID;

/**
 * Implemented by {@code ai} (use case REGISTRY_EXTRACTION). Used by the vehicle module's
 * RegistroDocumentoAdapter. The clear identity number of the owner never leaves the ai module:
 * it is returned as the OWNER_ID_HASH attribute.
 */
public interface RegistryDocumentReaderPort {

    RegistryExtraction extract(RegistryExtractionRequest request);

    /**
     * Records the analyst's decision on an inference (for example APPROVED, REJECTED, CORRECTED).
     */
    void recordHumanDecision(UUID inferenceId, String reviewer, String decision);
}
