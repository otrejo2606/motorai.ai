package ai.motoria.listing.dto;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.domain.Fuel;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.listing.domain.Province;
import ai.motoria.listing.domain.Transmission;
import ai.motoria.listing.domain.TrustLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ListingDetail (design D9, D9.1). Used by GET /listings/{id} and by every seller and backoffice operation.
 * {@code sellerId}, {@code recommendedPriceMin} and {@code recommendedPriceMax} are null for public viewers.
 */
public record ListingResponse(
        UUID id,
        ListingStatus status,
        String title,
        String description,
        ListingCategory category,
        UUID sellerId,
        UUID vehicleId,
        BigDecimal price,
        String currency,
        BigDecimal recommendedPriceMin,
        BigDecimal recommendedPriceMax,
        Integer modelYear,
        Integer mileage,
        String brand,
        String model,
        Fuel fuel,
        Province province,
        Transmission transmission,
        TrustLevel trustLevel,
        boolean negotiable,
        List<String> declaredEquipment,
        String maintenanceHistory,
        ListingVehicleResponse vehicle,
        List<ListingPhotoResponse> photos,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {
}
