package ai.motoria.listing.domain;

import ai.motoria.common.domain.ListingStatus;

import java.math.BigDecimal;

public record ListingFilter(
        ListingStatus status,
        String brand,
        String model,
        Integer yearFrom,
        Integer yearTo,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer maxMileage,
        Province province,
        Fuel fuel,
        Transmission transmission,
        TrustLevel trustLevel,
        ListingSort sort,
        int page,
        int size) {
}
