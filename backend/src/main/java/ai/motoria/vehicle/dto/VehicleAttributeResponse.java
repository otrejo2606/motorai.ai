package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.AttributeSource;
import ai.motoria.vehicle.domain.VehicleAttributeKey;

public record VehicleAttributeResponse(
        VehicleAttributeKey key,
        String value,
        AttributeSource source,
        boolean verified) {
}
