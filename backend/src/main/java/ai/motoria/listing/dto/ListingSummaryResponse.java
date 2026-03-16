package ai.motoria.listing.dto;

import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.common.domain.ListingStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ListingSummaryResponse(
        UUID id,
        String title,
        ListingStatus status,
        ListingCategory category,
        BigDecimal price,
        Integer modelYear,
        Integer mileage) {
}
