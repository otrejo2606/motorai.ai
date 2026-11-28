package ai.motoria.ai.integration;

import ai.motoria.ai.exception.AiErrorCodes;
import ai.motoria.ai.exception.LlmUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Generic HTTP adapter for providers that expose an OpenAI-compatible chat-completions endpoint
 * ({base-url}/chat/completions). Provider, base URL and model come from configuration; the key comes from the
 * environment (MOTORIA_AI_API_KEY). Owner gate 0.1 decides the concrete provider.
 */
@ApplicationScoped
@Typed(HttpLlmAdapter.class)
public class HttpLlmAdapter implements LlmPort {

    private final ObjectMapper objectMapper;
    private final String provider;
    private final Optional<String> baseUrl;
    private final String model;
    private final Optional<String> apiKey;
    private final Duration timeout;
    private final boolean sendTemperature;
    private final HttpClient httpClient;

    @Inject
    public HttpLlmAdapter(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "motoria.ai.provider", defaultValue = "stub") String provider,
            @ConfigProperty(name = "motoria.ai.base-url") Optional<String> baseUrl,
            @ConfigProperty(name = "motoria.ai.model", defaultValue = "stub-model") String model,
            @ConfigProperty(name = "motoria.ai.api-key") Optional<String> apiKey,
            @ConfigProperty(name = "motoria.ai.timeout", defaultValue = "PT90S") Duration timeout,
            @ConfigProperty(name = "motoria.ai.send-temperature", defaultValue = "true") boolean sendTemperature) {
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.sendTemperature = sendTemperature;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String endpoint = baseUrl.filter(value -> !value.isBlank())
                .map(value -> value.endsWith("/") ? value + "chat/completions" : value + "/chat/completions")
                .orElseThrow(() -> new LlmUnavailableException(AiErrorCodes.AI_NOT_CONFIGURED, "The model base URL is not configured"));
        String key = apiKey.filter(value -> !value.isBlank())
                .orElseThrow(() -> new LlmUnavailableException(AiErrorCodes.AI_NOT_CONFIGURED, "The model API key is not configured"));

        long start = System.nanoTime();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(buildBody(request))))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LlmUnavailableException(AiErrorCodes.AI_PROVIDER_ERROR, "Model provider returned status " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw new LlmUnavailableException(AiErrorCodes.AI_PROVIDER_ERROR, "Model provider returned no content");
            }
            JsonNode usage = root.path("usage");
            return new LlmResponse(
                    content.asText(),
                    provider,
                    model,
                    usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                    usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null,
                    latencyMs);
        } catch (IOException exception) {
            throw new LlmUnavailableException(AiErrorCodes.AI_UNAVAILABLE, "Model provider not reachable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException(AiErrorCodes.AI_UNAVAILABLE, "Model call interrupted", exception);
        }
    }

    private ObjectNode buildBody(LlmRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        if (sendTemperature) {
            body.put("temperature", 0);
        }
        body.put("max_tokens", request.maxOutputTokens());
        if (request.jsonOutput()) {
            body.putObject("response_format").put("type", "json_object");
        }
        ArrayNode messages = body.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", request.systemPrompt());
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        if (request.pngImagesBase64() == null || request.pngImagesBase64().isEmpty()) {
            user.put("content", request.userContent());
        } else {
            ArrayNode parts = user.putArray("content");
            parts.addObject().put("type", "text").put("text", request.userContent());
            for (String image : request.pngImagesBase64()) {
                ObjectNode part = parts.addObject();
                part.put("type", "image_url");
                part.putObject("image_url").put("url", "data:image/png;base64," + image);
            }
        }
        return body;
    }
}
