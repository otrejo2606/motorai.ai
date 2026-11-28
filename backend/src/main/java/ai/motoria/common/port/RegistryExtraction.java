package ai.motoria.common.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Validated result of reading a registry document. Every value carries the page it was cited from;
 * values without a valid citation were already discarded ({@code discardedValues}).
 *
 * @param succeeded   false when the model could not be called or its output was not valid JSON
 * @param failureCode stable code when {@code succeeded} is false
 */
public record RegistryExtraction(
        UUID inferenceId,
        boolean succeeded,
        String failureCode,
        ExtractedValue documentPlate,
        LocalDate documentDate,
        List<ExtractedValue> attributes,
        List<ExtractedEncumbrance> encumbrances,
        int discardedValues,
        int pageCount) {

    public static RegistryExtraction failed(UUID inferenceId, String failureCode, int pageCount) {
        return new RegistryExtraction(inferenceId, false, failureCode, null, null, List.of(), List.of(), 0, pageCount);
    }
}
