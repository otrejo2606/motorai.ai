package ai.motoria.common.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model of a vehicle for other modules. Owner attributes are never included.
 *
 * @param status REGISTRY_PENDING, REGISTRY_IN_REVIEW, REGISTRY_VERIFIED or REGISTRY_REJECTED
 */
public record VehicleSnapshot(
        UUID id,
        String countryCode,
        String plate,
        String status,
        boolean transferBlocked,
        String transferBlockReason,
        Instant registryCheckedAt,
        List<VehicleAttributeSnapshot> attributes) {

    public static final String STATUS_VERIFIED = "REGISTRY_VERIFIED";

    public boolean isRegistryVerified() {
        return STATUS_VERIFIED.equals(status);
    }

    public Optional<String> attribute(String key) {
        return attributes.stream()
                .filter(attribute -> attribute.key().equals(key))
                .map(VehicleAttributeSnapshot::value)
                .findFirst();
    }
}
