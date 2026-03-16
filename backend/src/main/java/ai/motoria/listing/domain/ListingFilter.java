package ai.motoria.listing.domain;

import ai.motoria.common.domain.ListingStatus;

import java.math.BigDecimal;

public record ListingFilter(
        ListingStatus status,
        ListingCategory category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer modelYear,
        int page,
        int size) {
}