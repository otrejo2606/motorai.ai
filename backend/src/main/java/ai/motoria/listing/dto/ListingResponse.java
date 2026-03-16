package ai.motoria.listing.dto;

import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.common.domain.ListingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ListingResponse(
        UUID id,
        String title,
        String description,
        ListingStatus status,
        ListingCategory category,
        UUID sellerId,
        UUID vehicleSpecId,
        BigDecimal price,
        BigDecimal recommendedPriceMin,
        BigDecimal recommendedPriceMax,
        Integer modelYear,
        Integer mileage,
        Instant createdAt,
        Instant updatedAt) {
}
