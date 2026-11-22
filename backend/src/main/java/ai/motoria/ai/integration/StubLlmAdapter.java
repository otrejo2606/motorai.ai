package ai.motoria.ai.integration;

import ai.motoria.ai.domain.AiUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import java.io.IOException;

/**
 * Deterministic adapter for dev and test (motoria.ai.provider=stub). It never calls a provider:
 * the registry extraction returns no values (every case goes to the analyst) and the listing copy is a plain template
 * built only from the input data.
 */
@ApplicationScoped
@Typed(StubLlmAdapter.class)
public class StubLlmAdapter implements LlmPort {

    public static final String PROVIDER = "stub";

    private final ObjectMapper objectMapper;

    @Inject
    public StubLlmAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String content = request.useCase() == AiUseCase.LISTING_COPY
                ? listingCopy(request.userContent())
                : emptyExtraction();
        return new LlmResponse(content, PROVIDER, "stub-model", 0, 0, 0L);
    }

    private String emptyExtraction() {
        ObjectNode root = objectMapper.createObjectNode();
        root.putNull("documentPlate");
        root.putNull("documentDate");
        root.putArray("attributes");
        root.putArray("encumbrances");
        return root.toString();
    }

    private String listingCopy(String userContent) {
        JsonNode input;
        try {
            input = objectMapper.readTree(userContent);
        } catch (IOException exception) {
            input = objectMapper.createObjectNode();
        }
        JsonNode registry = input.path("registry");
        String title = (registry.path("BRAND").asText("") + " " + registry.path("MODEL").asText("") + " "
                + registry.path("YEAR").asText("")).trim();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("title", title.isEmpty() ? "Vehiculo usado" : title);
        root.put("description", "Vehiculo con datos registrales verificados por Motoria. Kilometraje: "
                + input.path("seller").path("mileage").asText("-") + ".");
        return root.toString();
    }
}
