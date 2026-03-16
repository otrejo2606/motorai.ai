package ai.motoria.listing.dto;

import ai.motoria.listing.domain.ListingCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateListingRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 1024) String description,
        @NotNull ListingCategory category,
        @NotNull UUID vehicleSpecId,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @NotNull @Min(1950) @Max(2100) Integer modelYear,
        @NotNull @Min(0) Integer mileage) {
}