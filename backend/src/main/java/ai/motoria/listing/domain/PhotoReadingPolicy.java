package ai.motoria.listing.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Compares what the photos read with the vehicle (design D6 of add-captura-guiada-fotos). Only compares when both
 * values exist: without OCR in the classifier service, or before the registry lookup, nothing is reported.
 * <ul>
 *   <li>VIN photo: the VIN read, with letters and digits only, must equal the registry VIN (or chassis number).</li>
 *   <li>Dashboard photo: the odometer read may differ from the declared mileage by at most the larger of
 *       {@code toleranceKm} and {@code tolerancePercent} of the declared mileage.</li>
 * </ul>
 */
public final class PhotoReadingPolicy {

    public static final String VIN_SLOT = "VIN";
    public static final String ODOMETER_SLOT = "DASHBOARD_ODOMETER";

    private PhotoReadingPolicy() {
    }

    public static Optional<PhotoAnomaly> compare(String slot, String readVin, Integer readOdometerKm,
                                                 String registryVin, Integer declaredMileageKm,
                                                 int toleranceKm, int tolerancePercent) {
        if (VIN_SLOT.equals(slot)) {
            String read = normalizeVin(readVin);
            String registry = normalizeVin(registryVin);
            if (!read.isEmpty() && !registry.isEmpty() && !read.equals(registry)) {
                return Optional.of(PhotoAnomaly.VIN_MISMATCH);
            }
            return Optional.empty();
        }
        if (ODOMETER_SLOT.equals(slot) && readOdometerKm != null && declaredMileageKm != null) {
            long allowed = Math.max(Math.max(toleranceKm, 0),
                    (long) declaredMileageKm * Math.max(tolerancePercent, 0) / 100L);
            if (Math.abs((long) readOdometerKm - declaredMileageKm) > allowed) {
                return Optional.of(PhotoAnomaly.ODOMETER_MISMATCH);
            }
        }
        return Optional.empty();
    }

    static String normalizeVin(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
