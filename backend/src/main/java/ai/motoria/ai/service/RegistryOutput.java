package ai.motoria.ai.service;

import ai.motoria.common.port.ExtractedEncumbrance;
import ai.motoria.common.port.ExtractedValue;

import java.time.LocalDate;
import java.util.List;

/**
 * Model output after schema validation. Attributes may still contain OWNER_ID_NUMBER in clear: the service hashes it
 * before anything leaves the ai module or is stored.
 */
public record RegistryOutput(
        ExtractedValue documentPlate,
        LocalDate documentDate,
        List<ExtractedValue> attributes,
        List<ExtractedEncumbrance> encumbrances,
        int discardedValues) {
}
