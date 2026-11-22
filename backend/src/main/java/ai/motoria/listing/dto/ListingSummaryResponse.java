package ai.motoria.listing.dto;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.domain.Province;
import ai.motoria.listing.domain.TrustLevel;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * ListingSummary (design D9.1 item 4).
 */
public record ListingSummaryResponse(
        UUID id,
        ListingStatus status,
        String title,
        BigDecimal price,
        String currency,
        Integer modelYear,
        Integer mileage,
        String brand,
        String model,
        Province province,
        TrustLevel trustLevel,
        String coverPhotoUrl,
        UUID vehicleId) {
}
