package ai.motoria.listing.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VIN and odometer read in the photos versus the vehicle (design D6 of add-captura-guiada-fotos). Fictitious data.
 */
class PhotoReadingPolicyTest {

    private static final String REGISTRY_VIN = "JTDBR32E720123456";

    @Test
    void sameVinWithSeparatorsOrLowerCaseShouldFit() {
        assertTrue(PhotoReadingPolicy.compare("VIN", "jtdbr32e-7201 23456", null, REGISTRY_VIN, null, 500, 5).isEmpty());
    }

    @Test
    void differentVinShouldBeAnAnomaly() {
        assertEquals(Optional.of(PhotoAnomaly.VIN_MISMATCH),
                PhotoReadingPolicy.compare("VIN", "JTDBR32E720999999", null, REGISTRY_VIN, null, 500, 5));
    }

    @Test
    void vinShouldNotBeComparedWithoutBothValues() {
        assertTrue(PhotoReadingPolicy.compare("VIN", null, null, REGISTRY_VIN, null, 500, 5).isEmpty());
        assertTrue(PhotoReadingPolicy.compare("VIN", "JTDBR32E720999999", null, null, null, 500, 5).isEmpty());
    }

    @Test
    void odometerWithinTheToleranceShouldFit() {
        // Declared 40000: the tolerance is the larger of 500 km and 5 % (2000 km).
        assertTrue(PhotoReadingPolicy.compare("DASHBOARD_ODOMETER", null, 41999, null, 40000, 500, 5).isEmpty());
        assertTrue(PhotoReadingPolicy.compare("DASHBOARD_ODOMETER", null, 38000, null, 40000, 500, 5).isEmpty());
    }

    @Test
    void odometerBeyondTheToleranceShouldBeAnAnomaly() {
        assertEquals(Optional.of(PhotoAnomaly.ODOMETER_MISMATCH),
                PhotoReadingPolicy.compare("DASHBOARD_ODOMETER", null, 42001, null, 40000, 500, 5));
        assertEquals(Optional.of(PhotoAnomaly.ODOMETER_MISMATCH),
                PhotoReadingPolicy.compare("DASHBOARD_ODOMETER", null, 95000, null, 40000, 500, 5));
    }

    @Test
    void lowMileageShouldUseTheMinimumToleranceInKilometres() {
        // Declared 2000: 5 % is 100 km, so the 500 km minimum applies.
        assertTrue(PhotoReadingPolicy.compare("DASHBOARD_ODOMETER", null, 2450, null, 2000, 500, 5).isEmpty());
        assertEquals(Optional.of(PhotoAnomaly.ODOMETER_MISMATCH),
                PhotoReadingPolicy.compare("DASHBOARD_ODOMETER", null, 2600, null, 2000, 500, 5));
    }

    @Test
    void otherSlotsShouldNeverReportAnAnomaly() {
        assertTrue(PhotoReadingPolicy.compare("FRONT", "JTDBR32E720999999", 99999, REGISTRY_VIN, 1000, 500, 5).isEmpty());
    }
}
