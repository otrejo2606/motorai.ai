package ai.motoria.listing.integration;

import ai.motoria.listing.domain.PriceRange;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

/** Stub implementation. Replace with HttpAiPriceIntegration when AI module is available. */
@ApplicationScoped
public class StubAiPriceIntegration implements AiPriceIntegration {

    @Override
    public PriceRange recommendPriceRange(BigDecimal basePrice) {
        BigDecimal offset = basePrice.multiply(BigDecimal.valueOf(0.1));
        return new PriceRange(basePrice.subtract(offset), basePrice.add(offset));
    }
}