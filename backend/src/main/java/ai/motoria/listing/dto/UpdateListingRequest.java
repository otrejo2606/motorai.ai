package ai.motoria.listing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateListingRequest(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 1024) String description,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @NotNull @Min(1950) @Max(2100) Integer modelYear,
        @NotNull @Min(0) Integer mileage) {
}
