package ai.motoria.ai.service;

import ai.motoria.ai.domain.AiInference;
import ai.motoria.ai.domain.AiUseCase;
import ai.motoria.ai.exception.AiErrorCodes;
import ai.motoria.ai.integration.LlmPort;
import ai.motoria.ai.integration.LlmRequest;
import ai.motoria.ai.integration.LlmResponse;
import ai.motoria.ai.exception.LlmUnavailableException;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.DependencyUnavailableException;
import ai.motoria.common.port.ListingCopyPort;
import ai.motoria.common.port.ListingCopyRequest;
import ai.motoria.common.port.ListingCopySuggestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

/**
 * Use case LISTING_COPY: title and description built only from the registry data and what the seller declared.
 * The suggestion is returned to the seller, who must accept it; it is never applied automatically.
 */
@ApplicationScoped
public class ListingCopyService implements ListingCopyPort {

    static final int MAX_TITLE = 120;
    static final int MAX_DESCRIPTION = 1024;

    @Inject
    LlmPort llmPort;

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

    @Override
    public ListingCopySuggestion suggest(ListingCopyRequest request) {
        ObjectNode inputRefs = objectMapper.createObjectNode();
        inputRefs.put("listingId", request.listingId().toString());
        AiInference inference = AiInference.start(AiUseCase.LISTING_COPY, provider, model,
                AiPrompts.LISTING_COPY_PROMPT_VERSION, request.listingId(), inputRefs.toString(), null);

        LlmResponse response;
        try {
            response = llmPort.complete(new LlmRequest(
                    AiUseCase.LISTING_COPY,
                    AiPrompts.LISTING_COPY_SYSTEM,
                    userContent(request),
                    null,
                    1500,
                    true));
        } catch (LlmUnavailableException exception) {
            inference.fail(exception.getCode(), null);
            recorder.save(inference);
            throw new DependencyUnavailableException(AiErrorCodes.AI_UNAVAILABLE, "The writing assistant is not available");
        }

        String title;
        String description;
        try {
            JsonNode root = objectMapper.readTree(response.content() == null ? "" : response.content().trim());
            title = clean(root.path("title").asText(""), MAX_TITLE);
            description = clean(root.path("description").asText(""), MAX_DESCRIPTION);
        } catch (JsonProcessingException exception) {
            title = "";
            description = "";
        }
        if (title.isEmpty() || description.isEmpty()) {
            inference.fail(AiErrorCodes.AI_OUTPUT_INVALID, response.latencyMs());
            recorder.save(inference);
            throw new BusinessRuleException(AiErrorCodes.AI_OUTPUT_INVALID, "The assistant did not return a usable text");
        }

        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("title", title);
        stored.put("description", description);
        inference.succeed(stored.toString(), null, "suggestion pending seller approval", response.latencyMs(),
                response.inputTokens(), response.outputTokens(),
                costCalculator.cost(response.inputTokens(), response.outputTokens()));
        recorder.save(inference);
        return new ListingCopySuggestion(inference.getId(), title, description);
    }

    private String userContent(ListingCopyRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("language", "en".equalsIgnoreCase(request.language()) ? "en" : "es");
        ObjectNode registry = root.putObject("registry");
        for (Map.Entry<String, String> entry : request.registryAttributes().entrySet()) {
            registry.put(entry.getKey(), entry.getValue());
        }
        ObjectNode seller = root.putObject("seller");
        if (request.mileage() != null) {
            seller.put("mileage", request.mileage());
        }
        seller.put("transmission", request.transmission());
        seller.put("province", request.province());
        ArrayNode equipment = seller.putArray("declaredEquipment");
        if (request.declaredEquipment() != null) {
            request.declaredEquipment().forEach(equipment::add);
        }
        seller.put("maintenanceHistory", request.maintenanceHistory());
        seller.put("negotiable", request.negotiable());
        return root.toString();
    }

    private String clean(String value, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
