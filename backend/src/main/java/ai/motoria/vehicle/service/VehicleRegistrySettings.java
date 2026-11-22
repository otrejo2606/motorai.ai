package ai.motoria.vehicle.service;

import ai.motoria.vehicle.domain.RegistryReviewPolicy;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registry configuration: required attributes, confidence threshold, document age, automatic approval and the fee of
 * "que Motoria lo obtenga".
 */
@ApplicationScoped
public class VehicleRegistrySettings {

    @ConfigProperty(name = "motoria.vehicle.registry.required-attributes", defaultValue = "BRAND,MODEL,YEAR,VIN,OWNER_ID_HASH")
    List<String> requiredAttributes;

    @ConfigProperty(name = "motoria.vehicle.registry.min-confidence", defaultValue = "0.85")
    double minConfidence;

    @ConfigProperty(name = "motoria.vehicle.registry.document-max-age", defaultValue = "P30D")
    Duration documentMaxAge;

    @ConfigProperty(name = "motoria.vehicle.registry.auto-approve-enabled", defaultValue = "false")
    boolean autoApproveEnabled;

    @ConfigProperty(name = "motoria.vehicle.registry-request.fee")
    Optional<BigDecimal> registryRequestFee;

    @ConfigProperty(name = "motoria.vehicle.registry-request.currency", defaultValue = "CRC")
    String registryRequestCurrency;

    public List<VehicleAttributeKey> requiredKeys() {
        return requiredAttributes.stream()
                .map(value -> VehicleAttributeKey.valueOf(value.trim().toUpperCase(Locale.ROOT)))
                .toList();
    }

    public RegistryReviewPolicy reviewPolicy() {
        return new RegistryReviewPolicy(requiredKeys(), minConfidence, documentMaxAge, autoApproveEnabled);
    }

    public Duration documentMaxAge() {
        return documentMaxAge;
    }

    public Optional<BigDecimal> registryRequestFee() {
        return registryRequestFee;
    }

    public String registryRequestCurrency() {
        return registryRequestCurrency;
    }
}
