package ai.motoria.media.integration;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StubPhotoAngleClassifierAdapterTest {

    @Test
    void defaultStubShouldAnswerTheExpectedSlot() {
        StubPhotoAngleClassifierAdapter stub = new StubPhotoAngleClassifierAdapter(
                Optional.empty(), 0.99, Optional.empty(), Optional.empty());

        PhotoAngleClassification classification = stub.classify("REAR_LEFT", new byte[0], "image/jpeg");

        assertEquals("REAR_LEFT", classification.predictedSlot());
        assertEquals(0.99, classification.confidence(), 0.0001);
        assertEquals(StubPhotoAngleClassifierAdapter.MODEL_VERSION, classification.modelVersion());
        assertNull(classification.vin());
        assertNull(classification.odometerKm());
        assertEquals("stub", stub.providerName());
    }

    @Test
    void forcedSlotAndReadingsShouldBeDeterministic() {
        StubPhotoAngleClassifierAdapter stub = new StubPhotoAngleClassifierAdapter(
                Optional.of(" other "), 0.95, Optional.of("JTDBR32E720123456"), Optional.of(45000));

        assertEquals("OTHER", stub.classify("FRONT", new byte[0], "image/jpeg").predictedSlot());
        assertEquals("JTDBR32E720123456", stub.classify("VIN", new byte[0], "image/jpeg").vin());
        assertNull(stub.classify("VIN", new byte[0], "image/jpeg").odometerKm());
        assertEquals(45000, stub.classify("DASHBOARD_ODOMETER", new byte[0], "image/jpeg").odometerKm());
        assertNull(stub.classify("DASHBOARD_ODOMETER", new byte[0], "image/jpeg").vin());
    }
}
