package ai.motoria.ai.service;

import ai.motoria.ai.domain.AiInference;
import ai.motoria.ai.domain.AiUseCase;
import ai.motoria.ai.exception.AiErrorCodes;
import ai.motoria.ai.exception.InvalidModelOutputException;
import ai.motoria.ai.exception.LlmUnavailableException;
import ai.motoria.ai.integration.LlmPort;
import ai.motoria.ai.integration.LlmRequest;
import ai.motoria.ai.integration.LlmResponse;
import ai.motoria.ai.integration.PdfContent;
import ai.motoria.ai.integration.PdfTextExtractor;
import ai.motoria.common.exception.MotoriaException;
import ai.motoria.common.port.ExtractedValue;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.RegistryDocumentReaderPort;
import ai.motoria.common.port.RegistryExtraction;
import ai.motoria.common.port.RegistryExtractionRequest;
import ai.motoria.common.security.IdNumberHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Use case REGISTRY_EXTRACTION (design D4). Reads the registry PDF, sends its text (or page images) to the model as
 * data, validates the JSON against the schema, discards values without a page citation, hashes the owner's identity
 * number and records the inference. The clear identity number is never stored nor returned.
 */
@ApplicationScoped
public class RegistryExtractionService implements RegistryDocumentReaderPort {

    static final String OWNER_ID_NUMBER = "OWNER_ID_NUMBER";
    static final String OWNER_ID_HASH = "OWNER_ID_HASH";
    static final Set<String> CONFIDENCE_KEYS = Set.of("BRAND", "MODEL", "YEAR", "VIN", OWNER_ID_HASH);
    private static final String READ_PURPOSE = "AI_REGISTRY_EXTRACTION";

    @Inject
    MediaPort mediaPort;

    @Inject
    PdfTextExtractor pdfTextExtractor;

    @Inject
    LlmPort llmPort;

    @Inject
    RegistryExtractionValidator validator;

    @Inject
    IdNumberHasher idNumberHasher;

    @Inject
    AiInferenceRecorder recorder;

    @Inject
    AiCostCalculator costCalculator;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "motoria.ai.provider", defaultValue = "stub")
    String provider;

    @ConfigProperty(name = "motoria.ai.model", defaultValue = "stub-model")
    String model;

    @ConfigProperty(name = "motoria.ai.max-output-tokens", defaultValue = "4000")
    int maxOutputTokens;

    @ConfigProperty(name = "motoria.media.pdf.max-pages", defaultValue = "10")
    int maxPages;

    @ConfigProperty(name = "motoria.ai.pdf.min-text-chars", defaultValue = "200")
    int minTextChars;

    @Override
    public RegistryExtraction extract(RegistryExtractionRequest request) {
        ObjectNode inputRefs = objectMapper.createObjectNode();
        inputRefs.put("documentMediaId", request.documentMediaId().toString());
        AiInference inference = AiInference.start(AiUseCase.REGISTRY_EXTRACTION, provider, model,
                AiPrompts.REGISTRY_PROMPT_VERSION, request.vehicleId(), inputRefs.toString(), request.correlationId());

        PdfContent content;
        try {
            byte[] pdf = mediaPort.readContent(request.documentMediaId(), READ_PURPOSE);
            content = pdfTextExtractor.extract(pdf, maxPages, minTextChars);
        } catch (MotoriaException exception) {
            return fail(inference, AiErrorCodes.AI_DOCUMENT_UNAVAILABLE, 0, null);
        } catch (UncheckedIOException exception) {
            return fail(inference, AiErrorCodes.AI_DOCUMENT_UNREADABLE, 0, null);
        }
        inputRefs.put("pages", content.pageCount());
        inputRefs.put("pageImages", content.pngImagesBase64().size());

        LlmResponse response;
        try {
            response = llmPort.complete(new LlmRequest(
                    AiUseCase.REGISTRY_EXTRACTION,
                    AiPrompts.REGISTRY_SYSTEM,
                    userContent(request, content),
                    content.pngImagesBase64(),
                    maxOutputTokens,
                    true));
        } catch (LlmUnavailableException exception) {
            Log.warnv("Registry extraction failed for vehicle {0}: {1}", request.vehicleId(), exception.getCode());
            return fail(inference, exception.getCode(), content.pageCount(), null);
        }

        RegistryOutput output;
        try {
            output = validator.validate(response.content(), content.pageCount());
        } catch (InvalidModelOutputException exception) {
            return fail(inference, AiErrorCodes.AI_OUTPUT_INVALID, content.pageCount(), response.latencyMs());
        }

        List<ExtractedValue> attributes = protectOwnerIdNumber(output.attributes());
        double confidence = minimumConfidence(attributes);
        RegistryExtraction extraction = new RegistryExtraction(
                inference.getId(),
                true,
                null,
                output.documentPlate(),
                output.documentDate(),
                attributes,
                output.encumbrances(),
                output.discardedValues(),
                content.pageCount());

        inference.succeed(
                storedOutput(extraction),
                BigDecimal.valueOf(confidence).setScale(4, RoundingMode.HALF_UP),
                "values=" + attributes.size() + "; discardedWithoutCitationOrInvalid=" + output.discardedValues()
                        + "; encumbrances=" + output.encumbrances().size(),
                response.latencyMs(),
                response.inputTokens(),
                response.outputTokens(),
                costCalculator.cost(response.inputTokens(), response.outputTokens()));
        recorder.save(inference);
        return extraction;
    }

    @Override
    public void recordHumanDecision(java.util.UUID inferenceId, String reviewer, String decision) {
        if (inferenceId != null) {
            recorder.recordHumanDecision(inferenceId, reviewer, decision);
        }
    }

    /**
     * Replaces OWNER_ID_NUMBER by its keyed hash (OWNER_ID_HASH). If the hash key is missing the value is dropped and
     * the analyst enters it.
     */
    List<ExtractedValue> protectOwnerIdNumber(List<ExtractedValue> attributes) {
        List<ExtractedValue> protectedValues = new ArrayList<>(attributes.size());
        for (ExtractedValue attribute : attributes) {
            if (!OWNER_ID_NUMBER.equals(attribute.key())) {
                protectedValues.add(attribute);
                continue;
            }
            try {
                String hash = idNumberHasher.hash(attribute.value());
                if (hash != null) {
                    protectedValues.add(new ExtractedValue(OWNER_ID_HASH, hash, attribute.page(), attribute.confidence()));
                }
            } catch (MotoriaException exception) {
                Log.warn("Owner identity number dropped: the identity hash key is not configured");
            }
        }
        return protectedValues;
    }

    private double minimumConfidence(List<ExtractedValue> attributes) {
        double minimum = 1.0;
        int found = 0;
        for (ExtractedValue attribute : attributes) {
            if (CONFIDENCE_KEYS.contains(attribute.key())) {
                minimum = Math.min(minimum, attribute.confidence());
                found++;
            }
        }
        return found == CONFIDENCE_KEYS.size() ? minimum : 0.0;
    }

    private String userContent(RegistryExtractionRequest request, PdfContent content) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("countryCode", request.countryCode());
        root.put("pageCount", content.pageCount());
        ArrayNode pages = root.putArray("documentPages");
        for (int index = 0; index < content.pageTexts().size(); index++) {
            ObjectNode page = pages.addObject();
            page.put("page", index + 1);
            page.put("text", content.pageTexts().get(index));
        }
        return root.toString();
    }

    private String storedOutput(RegistryExtraction extraction) {
        try {
            return objectMapper.writeValueAsString(extraction);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return null;
        }
    }

    private RegistryExtraction fail(AiInference inference, String code, int pageCount, Long latencyMs) {
        inference.fail(code, latencyMs);
        recorder.save(inference);
        return RegistryExtraction.failed(inference.getId(), code, pageCount);
    }
}
