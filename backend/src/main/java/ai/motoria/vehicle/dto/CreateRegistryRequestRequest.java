package ai.motoria.vehicle.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * {@code acceptedFee} must equal the configured fee shown to the seller (registryRequestFee.amount).
 */
public record CreateRegistryRequestRequest(
        @NotNull @DecimalMin("0.0") BigDecimal acceptedFee) {
}
