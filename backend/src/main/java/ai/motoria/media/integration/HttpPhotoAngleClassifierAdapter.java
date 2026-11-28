package ai.motoria.media.integration;

import ai.motoria.media.exception.MediaErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client of Motoria's own inference service (design D3 of add-captura-guiada-fotos): POST {url} with JSON
 * {"expectedSlot", "mimeType", "imageBase64"}; the answer carries "modelVersion", "predictions" (slot and confidence)
 * and, only when the service has OCR, "vin" and "odometerKm". The URL comes from MOTORIA_PHOTO_CLASSIFIER_URL; a token
 * is optional (the service runs on the private network). The image is never logged.
 */
@ApplicationScoped
@Typed(HttpPhotoAngleClassifierAdapter.class)
public class HttpPhotoAngleClassifierAdapter implements PhotoAngleClassifierPort {

    public static final String PROVIDER = "http";
    /** The 14 shots of the photo protocol plus OTHER (design D3); any other label from the model is ignored. */
    static final Set<String> KNOWN_SLOTS = Set.of("FRONT", "REAR", "LEFT", "RIGHT", "FRONT_LEFT", "FRONT_RIGHT",
            "REAR_LEFT", "REAR_RIGHT", "INTERIOR_FRONT", "INTERIOR_REAR", "DASHBOARD_ODOMETER", "ENGINE", "TIRES",
            "VIN", "OTHER");
    private static final Pattern VIN_CHARS = Pattern.compile("[A-Z0-9]{5,30}");
    private static final int MAX_MODEL_VERSION = 60;
    private static final int MAX_ODOMETER_KM = 2_000_000;

    private final ObjectMapper objectMapper;
    private final Optional<String> url;
    private final Optional<String> token;
    private final Duration timeout;
    private final HttpClient httpClient;

    @Inject
    public HttpPhotoAngleClassifierAdapter(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "motoria.media.angle-check.url") Optional<String> url,
            @ConfigProperty(name = "motoria.media.angle-check.token") Optional<String> token,
            @ConfigProperty(name = "motoria.media.angle-check.timeout", defaultValue = "PT5S") Duration timeout) {
        this.objectMapper = objectMapper;
        this.url = url;
        this.token = token;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public PhotoAngleClassification classify(String expectedSlot, byte[] image, String mimeType) {
        String endpoint = url.filter(value -> !value.isBlank())
                .map(String::trim)
                .orElseThrow(() -> new PhotoAngleClassifierUnavailableException(
                        MediaErrorCodes.PHOTO_CLASSIFIER_NOT_CONFIGURED, "The photo classifier URL is not configured"));
        long start = System.nanoTime();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(expectedSlot, image, mimeType)));
            token.filter(value -> !value.isBlank())
                    .ifPresent(value -> builder.header("Authorization", "Bearer " + value.trim()));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            long latencyMs = (System.nanoTime() - start) / 1_000_000L;
            return parse(response.statusCode(), response.body(), latencyMs);
        } catch (IllegalArgumentException exception) {
            throw new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_NOT_CONFIGURED,
                    "The photo classifier URL is not valid", exception);
        } catch (IOException exception) {
            throw new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE,
                    "The photo classifier is not reachable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE,
                    "The photo classifier call was interrupted", exception);
        }
    }

    String requestBody(String expectedSlot, byte[] image, String mimeType) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expectedSlot", expectedSlot);
        body.put("mimeType", mimeType);
        body.put("imageBase64", Base64.getEncoder().encodeToString(image));
        return body.toString();
    }

    /**
     * Reads the answer of the inference service. 400 means the service could not read the image; 401 and 403 mean a
     * configuration problem; any other status outside 2xx, an unreadable body or a body without a valid prediction
     * makes the check UNAVAILABLE.
     */
    PhotoAngleClassification parse(int status, String body, long latencyMs) {
        if (status == 400 || status == 415 || status == 422) {
            throw new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_IMAGE_UNREADABLE,
                    "The photo classifier could not read the image (status " + status + ")");
        }
        if (status == 401 || status == 403) {
            throw new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_NOT_CONFIGURED,
                    "The photo classifier refused the call (status " + status + ")");
        }
        if (status < 200 || status >= 300) {
            throw new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE,
                    "The photo classifier returned status " + status);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body == null ? "" : body);
        } catch (IOException exception) {
            throw invalid("The photo classifier answer is not JSON");
        }
        if (root == null || !root.isObject()) {
            throw invalid("The photo classifier answer is not a JSON object");
        }
        String modelVersion = root.path("modelVersion").asText("").trim();
        if (modelVersion.isEmpty()) {
            throw invalid("The photo classifier answer has no model version");
        }
        if (modelVersion.length() > MAX_MODEL_VERSION) {
            modelVersion = modelVersion.substring(0, MAX_MODEL_VERSION);
        }
        String bestSlot = null;
        double bestConfidence = -1;
        for (JsonNode prediction : root.path("predictions")) {
            String slot = prediction.path("slot").asText("").trim().toUpperCase(Locale.ROOT);
            JsonNode confidenceNode = prediction.path("confidence");
            if (!KNOWN_SLOTS.contains(slot) || !confidenceNode.isNumber()) {
                continue;
            }
            double confidence = confidenceNode.asDouble();
            if (confidence < 0 || confidence > 1) {
                continue;
            }
            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestSlot = slot;
            }
        }
        if (bestSlot == null) {
            throw invalid("The photo classifier answer has no valid prediction");
        }
        return new PhotoAngleClassification(bestSlot, bestConfidence, modelVersion, vin(root.path("vin")),
                odometer(root.path("odometerKm")), latencyMs);
    }

    static String vin(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return VIN_CHARS.matcher(value).matches() ? value : null;
    }

    static Integer odometer(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        long value = Math.round(node.asDouble());
        return value >= 0 && value <= MAX_ODOMETER_KM ? (int) value : null;
    }

    private PhotoAngleClassifierUnavailableException invalid(String message) {
        return new PhotoAngleClassifierUnavailableException(MediaErrorCodes.PHOTO_CLASSIFIER_INVALID_RESPONSE, message);
    }
}
