package ai.motoria.listing.integration;

import ai.motoria.listing.domain.PriceRange;

import java.math.BigDecimal;

public interface AiPriceIntegration {
    PriceRange recommendPriceRange(BigDecimal basePrice);
}