package ai.motoria.ai.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Estimated cost in USD from the configured price per 1000 tokens.
 */
@ApplicationScoped
public class AiCostCalculator {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    @ConfigProperty(name = "motoria.ai.cost.input-per-1k", defaultValue = "0")
    BigDecimal inputPer1k;

    @ConfigProperty(name = "motoria.ai.cost.output-per-1k", defaultValue = "0")
    BigDecimal outputPer1k;

    public BigDecimal cost(Integer inputTokens, Integer outputTokens) {
        if (inputTokens == null && outputTokens == null) {
            return null;
        }
        BigDecimal input = BigDecimal.valueOf(inputTokens == null ? 0 : inputTokens).multiply(inputPer1k);
        BigDecimal output = BigDecimal.valueOf(outputTokens == null ? 0 : outputTokens).multiply(outputPer1k);
        return input.add(output).divide(THOUSAND, 6, RoundingMode.HALF_UP);
    }
}
