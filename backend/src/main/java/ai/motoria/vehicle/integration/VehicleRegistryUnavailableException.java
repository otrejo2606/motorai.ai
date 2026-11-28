package ai.motoria.vehicle.integration;

import ai.motoria.common.exception.DependencyUnavailableException;

/**
 * The vehicle registry could not answer (not configured, credentials rejected, error or timeout). The lookup endpoint
 * never turns it into an HTTP error: the seller is sent to the registry PDF instead.
 */
public class VehicleRegistryUnavailableException extends DependencyUnavailableException {

    public VehicleRegistryUnavailableException(String code, String message) {
        super(code, message);
    }

    public VehicleRegistryUnavailableException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
