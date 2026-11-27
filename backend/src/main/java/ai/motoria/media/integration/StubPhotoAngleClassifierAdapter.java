package ai.motoria.media.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic classifier for dev and tests (motoria.media.angle-check.provider=stub, the default). It never calls
 * out and never looks at the image: it answers the expected slot with the configured confidence. To try the other
 * paths in development, motoria.media.angle-check.stub.predicted-slot forces the predicted slot (MISMATCH),
 * a low stub.confidence gives UNCERTAIN, and stub.vin / stub.odometer-km simulate an OCR reading.
 */
@ApplicationScoped
@Typed(StubPhotoAngleClassifierAdapter.class)
public class StubPhotoAngleClassifierAdapter implements PhotoAngleClassifierPort {

    public static final String PROVIDER = "stub";
    static final String MODEL_VERSION = "stub-1";

    private final Optional<String> predictedSlot;
    private final double confidence;
    private final Optional<String> vin;
    private final Optional<Integer> odometerKm;

    @Inject
    public StubPhotoAngleClassifierAdapter(
            @ConfigProperty(name = "motoria.media.angle-check.stub.predicted-slot") Optional<String> predictedSlot,
            @ConfigProperty(name = "motoria.media.angle-check.stub.confidence", defaultValue = "0.99") double confidence,
            @ConfigProperty(name = "motoria.media.angle-check.stub.vin") Optional<String> vin,
            @ConfigProperty(name = "motoria.media.angle-check.stub.odometer-km") Optional<Integer> odometerKm) {
        this.predictedSlot = predictedSlot;
        this.confidence = confidence;
        this.vin = vin;
        this.odometerKm = odometerKm;
    }

    @Override
    public PhotoAngleClassification classify(String expectedSlot, byte[] image, String mimeType) {
        String slot = predictedSlot.filter(value -> !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .orElse(expectedSlot);
        String readVin = "VIN".equals(expectedSlot) ? vin.filter(value -> !value.isBlank()).orElse(null) : null;
        Integer readOdometer = "DASHBOARD_ODOMETER".equals(expectedSlot) ? odometerKm.orElse(null) : null;
        return new PhotoAngleClassification(slot, confidence, MODEL_VERSION, readVin, readOdometer, 0L);
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }
}
