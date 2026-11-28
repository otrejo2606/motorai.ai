package ai.motoria.vehicle.service;

import ai.motoria.vehicle.domain.RegistryReviewPolicy;
import ai.motoria.vehicle.domain.VehicleAttributeKey;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registry configuration: required attributes, confidence threshold, document age, automatic approval, the fee of
 * "que Motoria lo obtenga" and the registry agreement lookup (maximum age, automatic approval, daily quota).
 */
@ApplicationScoped
public class VehicleRegistrySettings {

    static final Duration DEFAULT_AGREEMENT_MAX_AGE = Duration.ofDays(90);
    static final String DEFAULT_QUOTA_ZONE = "America/Costa_Rica";
    static final int DEFAULT_DAILY_QUOTA = 200;

    @ConfigProperty(name = "motoria.vehicle.registry.required-attributes", defaultValue = "BRAND,MODEL,YEAR,VIN,OWNER_ID_HASH")
    List<String> requiredAttributes;

    @ConfigProperty(name = "motoria.vehicle.registry.min-confidence", defaultValue = "0.85")
    double minConfidence;

    @ConfigProperty(name = "motoria.vehicle.registry.document-max-age", defaultValue = "P30D")
    Duration documentMaxAge;

    @ConfigProperty(name = "motoria.vehicle.registry.auto-approve-enabled", defaultValue = "false")
    boolean autoApproveEnabled;

    @ConfigProperty(name = "motoria.vehicle.registry.agreement-max-age", defaultValue = "P90D")
    Duration agreementMaxAge;

    @ConfigProperty(name = "motoria.vehicle.registry.agreement-auto-approve", defaultValue = "true")
    boolean agreementAutoApprove = true;

    @ConfigProperty(name = "motoria.vehicle.registry.daily-quota", defaultValue = "200")
    int dailyQuota = DEFAULT_DAILY_QUOTA;

    @ConfigProperty(name = "motoria.vehicle.registry.quota-zone", defaultValue = DEFAULT_QUOTA_ZONE)
    String quotaZone;

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

    /**
     * Maximum age of registry agreement data (Credid); within it the registry is not queried again.
     */
    public Duration agreementMaxAge() {
        return agreementMaxAge == null ? DEFAULT_AGREEMENT_MAX_AGE : agreementMaxAge;
    }

    public boolean agreementAutoApprove() {
        return agreementAutoApprove;
    }

    public int dailyQuota() {
        return dailyQuota;
    }

    public ZoneId quotaZone() {
        return ZoneId.of(quotaZone == null ? DEFAULT_QUOTA_ZONE : quotaZone);
    }

    public Optional<BigDecimal> registryRequestFee() {
        return registryRequestFee;
    }

    public String registryRequestCurrency() {
        return registryRequestCurrency;
    }
}
