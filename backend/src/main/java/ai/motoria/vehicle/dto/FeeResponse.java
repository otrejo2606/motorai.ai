package ai.motoria.vehicle.dto;

import java.math.BigDecimal;

public record FeeResponse(
        BigDecimal amount,
        String currency) {
}
