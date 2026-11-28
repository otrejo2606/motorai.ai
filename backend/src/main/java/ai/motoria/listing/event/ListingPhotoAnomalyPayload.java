package ai.motoria.listing.event;

import java.util.List;
import java.util.UUID;

/**
 * vehicle.registry.anomaly.detected published by {@code listing} when a photo reads something that does not fit the
 * vehicle (add-captura-guiada-fotos). Same shape as the vehicle module payload (vehicleId, sellerId, anomalies), so
 * the notification consumer handles both; listingId is extra and ignored by consumers that do not need it. No VIN,
 * plate or odometer value travels in the event.
 *
 * @param anomalies PhotoAnomaly codes (VIN_MISMATCH, ODOMETER_MISMATCH)
 */
public record ListingPhotoAnomalyPayload(
        UUID vehicleId,
        UUID sellerId,
        List<String> anomalies,
        UUID listingId) {
}
