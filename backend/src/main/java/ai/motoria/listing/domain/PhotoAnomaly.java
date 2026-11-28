package ai.motoria.listing.domain;

/**
 * What a photo reads does not fit the vehicle (add-captura-guiada-fotos). VIN_MISMATCH: the VIN read in the VIN photo
 * is not the registry VIN (or chassis number). ODOMETER_MISMATCH: the odometer read in the dashboard photo differs from
 * the declared mileage beyond the tolerance. Both only exist when the classifier service reads text (owner gate 0.3).
 * They do not block the review: the seller is told and the analyst sees them. Published with the existing event
 * vehicle.registry.anomaly.detected.
 */
public enum PhotoAnomaly {
    VIN_MISMATCH,
    ODOMETER_MISMATCH
}
