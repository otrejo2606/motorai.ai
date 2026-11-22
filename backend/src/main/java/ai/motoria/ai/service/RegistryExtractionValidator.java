package ai.motoria.ai.service;

import ai.motoria.ai.exception.InvalidModelOutputException;
import ai.motoria.common.port.ExtractedEncumbrance;
import ai.motoria.common.port.ExtractedValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates the registry extraction output against the expected schema (design D4): known keys only, every value
 * must cite a page inside the document, confidence in [0, 1], bounded lengths. Invalid values are discarded and
 * counted; output that is not a JSON object is rejected entirely.
 */
@ApplicationScoped
public class RegistryExtractionValidator {

    static final Set<String> ATTRIBUTE_KEYS = Set.of(
            "BRAND", "MODEL", "STYLE", "YEAR", "COLOR", "VIN", "ENGINE_NUMBER", "DISPLACEMENT_CC", "FUEL",
            "DRIVETRAIN", "DOORS", "CAPACITY", "OWNER_ID_NUMBER", "OWNER_NAME");
    static final Set<String> ENCUMBRANCE_TYPES = Set.of("PLEDGE", "SEIZURE", "ANNOTATION", "COLLISION", "OTHER");
    private static final Pattern YEAR = Pattern.compile("(19|20)[0-9]{2}");
    private static final int MAX_VALUE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final ObjectMapper objectMapper;

    @Inject
    public RegistryExtractionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RegistryOutput validate(String modelOutput, int pageCount) {
        JsonNode root;
        try {
            root = objectMapper.readTree(modelOutput == null ? "" : modelOutput.trim());
        } catch (JsonProcessingException exception) {
            throw new InvalidModelOutputException("The model output is not valid JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new InvalidModelOutputException("The model output is not a JSON object");
        }

        int[] discarded = {0};
        ExtractedValue plate = readValue("DOCUMENT_PLATE", root.get("documentPlate"), pageCount, discarded);
        ExtractedValue dateValue = readValue("DOCUMENT_DATE", root.get("documentDate"), pageCount, discarded);
        LocalDate documentDate = parseDate(dateValue, discarded);

        Map<String, ExtractedValue> attributes = new LinkedHashMap<>();
        JsonNode attributeNodes = root.get("attributes");
        if (attributeNodes != null && attributeNodes.isArray()) {
            for (JsonNode node : attributeNodes) {
                String key = node.path("key").asText("");
                if (!ATTRIBUTE_KEYS.contains(key)) {
                    discarded[0]++;
                    continue;
                }
                ExtractedValue value = readValue(key, node, pageCount, discarded);
                if (value == null) {
                    continue;
                }
                if ("YEAR".equals(key) && !YEAR.matcher(value.value()).matches()) {
                    discarded[0]++;
                    continue;
                }
                ExtractedValue previous = attributes.get(key);
                if (previous == null || value.confidence() > previous.confidence()) {
                    attributes.put(key, value);
                }
            }
        }

        List<ExtractedEncumbrance> encumbrances = new ArrayList<>();
        JsonNode encumbranceNodes = root.get("encumbrances");
        if (encumbranceNodes != null && encumbranceNodes.isArray()) {
            for (JsonNode node : encumbranceNodes) {
                ExtractedEncumbrance encumbrance = readEncumbrance(node, pageCount);
                if (encumbrance == null) {
                    discarded[0]++;
                } else {
                    encumbrances.add(encumbrance);
                }
            }
        }
        return new RegistryOutput(plate, documentDate, List.copyOf(attributes.values()), encumbrances, discarded[0]);
    }

    /**
     * Returns null (and counts a discard) when the value has no valid page citation, no text or an invalid confidence.
     * A missing or null node is not counted: the field is simply absent.
     */
    private ExtractedValue readValue(String key, JsonNode node, int pageCount, int[] discarded) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.path("value").isValueNode() ? node.path("value").asText("").trim() : "";
        Integer page = readPage(node.get("page"), pageCount);
        Double confidence = readConfidence(node.get("confidence"));
        if (value.isEmpty() || value.length() > MAX_VALUE_LENGTH || page == null || confidence == null) {
            discarded[0]++;
            return null;
        }
        return new ExtractedValue(key, value, page, confidence);
    }

    private ExtractedEncumbrance readEncumbrance(JsonNode node, int pageCount) {
        String type = node.path("type").asText("");
        Integer page = readPage(node.get("page"), pageCount);
        Double confidence = readConfidence(node.get("confidence"));
        if (page == null || confidence == null) {
            return null;
        }
        String description = node.path("description").asText("").trim();
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return new ExtractedEncumbrance(ENCUMBRANCE_TYPES.contains(type) ? type : "OTHER", description, page, confidence);
    }

    private Integer readPage(JsonNode node, int pageCount) {
        if (node == null || !node.isIntegralNumber()) {
            return null;
        }
        int page = node.asInt();
        return page >= 1 && page <= pageCount ? page : null;
    }

    private Double readConfidence(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double confidence = node.asDouble();
        return confidence >= 0.0 && confidence <= 1.0 ? confidence : null;
    }

    private LocalDate parseDate(ExtractedValue dateValue, int[] discarded) {
        if (dateValue == null) {
            return null;
        }
        try {
            return LocalDate.parse(dateValue.value());
        } catch (DateTimeParseException exception) {
            discarded[0]++;
            return null;
        }
    }
}
