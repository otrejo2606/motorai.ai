package ai.motoria.vehicle.dto;

import ai.motoria.vehicle.domain.AttributeSource;
import ai.motoria.vehicle.domain.VehicleAttributeKey;

import java.math.BigDecimal;

/**
 * Analyst view of a value: {@code aiValue} keeps what the AI read when the analyst corrected it.
 */
public record ReviewAttributeResponse(
        VehicleAttributeKey key,
        String value,
        AttributeSource source,
        BigDecimal confidence,
        Integer page,
        String aiValue,
        String correctedBy) {
}
