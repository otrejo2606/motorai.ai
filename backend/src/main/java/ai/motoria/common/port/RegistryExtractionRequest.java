package ai.motoria.common.port;

import java.util.UUID;

public record RegistryExtractionRequest(
        UUID vehicleId,
        UUID documentMediaId,
        String countryCode,
        UUID correlationId) {
}
