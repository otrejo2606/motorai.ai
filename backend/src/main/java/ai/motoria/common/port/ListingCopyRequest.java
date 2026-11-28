package ai.motoria.common.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Only the data the copy may use: verified registry attributes (never owner data) and what the seller declared.
 */
public record ListingCopyRequest(
        UUID listingId,
        String language,
        Map<String, String> registryAttributes,
        Integer mileage,
        String transmission,
        String province,
        List<String> declaredEquipment,
        String maintenanceHistory,
        boolean negotiable,
        BigDecimal price) {
}
