package ai.motoria.media.integration;

import ai.motoria.media.exception.MediaErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract of Motoria's own inference service (design D3 of add-captura-guiada-fotos). Fictitious data.
 */
class HttpPhotoAngleClassifierAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpPhotoAngleClassifierAdapter adapter = new HttpPhotoAngleClassifierAdapter(
            objectMapper, Optional.of("http://10.90.0.10:8000/v1/classify"), Optional.empty(), Duration.ofSeconds(2));

    @Test
    void topPredictionShouldBeTheOneWithTheHighestConfidence() {
        PhotoAngleClassification classification = adapter.parse(200, """
                {"modelVersion": "angle-cls-2027.01",
                 "predictions": [{"slot": "FRONT_LEFT", "confidence": 0.12},
                                 {"slot": "FRONT", "confidence": 0.86},
                                 {"slot": "OTHER", "confidence": 0.02}]}
                """, 42L);

        assertEquals("FRONT", classification.predictedSlot());
        assertEquals(0.86, classification.confidence(), 0.0001);
        assertEquals("angle-cls-2027.01", classification.modelVersion());
        assertEquals(42L, classification.latencyMs());
        assertNull(classification.vin());
        assertNull(classification.odometerKm());
    }

    @Test
    void invalidPredictionsShouldBeIgnored() {
        PhotoAngleClassification classification = adapter.parse(200, """
                {"modelVersion": "v2",
                 "predictions": [{"slot": "front-left; drop", "confidence": 0.99},
                                 {"slot": "CAR", "confidence": 0.95},
                                 {"slot": "REAR", "confidence": 1.7},
                                 {"slot": "REAR", "confidence": "0.9"},
                                 {"slot": "rear", "confidence": 0.61}]}
                """, 5L);

        assertEquals("REAR", classification.predictedSlot());
        assertEquals(0.61, classification.confidence(), 0.0001);
    }

    @Test
    void readingsShouldBeKeptOnlyWhenTheyAreValid() {
        PhotoAngleClassification withReadings = adapter.parse(200, """
                {"modelVersion": "v3-ocr", "predictions": [{"slot": "VIN", "confidence": 0.95}],
                 "vin": "jtdbr32e-720123456", "odometerKm": 45210.4}
                """, 5L);
        PhotoAngleClassification withInvalidReadings = adapter.parse(200, """
                {"modelVersion": "v3-ocr", "predictions": [{"slot": "VIN", "confidence": 0.95}],
                 "vin": "x", "odometerKm": -3}
                """, 5L);

        assertEquals("JTDBR32E720123456", withReadings.vin());
        assertEquals(45210, withReadings.odometerKm());
        assertNull(withInvalidReadings.vin());
        assertNull(withInvalidReadings.odometerKm());
    }

    @Test
    void statusesShouldMapToStableCodes() {
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_IMAGE_UNREADABLE, codeOf(400, "{}"));
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_NOT_CONFIGURED, codeOf(401, "{}"));
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_NOT_CONFIGURED, codeOf(403, "{}"));
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE, codeOf(500, "{}"));
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_UNAVAILABLE, codeOf(503, ""));
    }

    @Test
    void unusableAnswersShouldBeInvalidResponses() {
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_INVALID_RESPONSE, codeOf(200, "not json"));
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_INVALID_RESPONSE, codeOf(200, "[]"));
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_INVALID_RESPONSE, codeOf(200, """
                {"predictions": [{"slot": "FRONT", "confidence": 0.9}]}
                """));
        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_INVALID_RESPONSE, codeOf(200, """
                {"modelVersion": "v1", "predictions": []}
                """));
    }

    @Test
    void missingUrlShouldBeNotConfigured() {
        HttpPhotoAngleClassifierAdapter unconfigured = new HttpPhotoAngleClassifierAdapter(
                objectMapper, Optional.empty(), Optional.empty(), Duration.ofSeconds(2));

        PhotoAngleClassifierUnavailableException exception = assertThrows(PhotoAngleClassifierUnavailableException.class,
                () -> unconfigured.classify("FRONT", new byte[] {1, 2, 3}, "image/jpeg"));

        assertEquals(MediaErrorCodes.PHOTO_CLASSIFIER_NOT_CONFIGURED, exception.getCode());
    }

    @Test
    void requestBodyShouldCarryTheSlotAndTheImageOnly() {
        JsonNode body = readTree(adapter.requestBody("VIN", new byte[] {1, 2, 3}, "image/jpeg"));

        assertEquals("VIN", body.path("expectedSlot").asText());
        assertEquals("image/jpeg", body.path("mimeType").asText());
        assertEquals("AQID", body.path("imageBase64").asText());
        assertEquals(3, body.size());
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String codeOf(int status, String body) {
        return assertThrows(PhotoAngleClassifierUnavailableException.class, () -> adapter.parse(status, body, 1L)).getCode();
    }
}
