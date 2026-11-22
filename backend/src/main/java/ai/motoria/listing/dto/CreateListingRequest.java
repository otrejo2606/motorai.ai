package ai.motoria.listing.dto;

import ai.motoria.listing.domain.Province;
import ai.motoria.listing.domain.Transmission;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Design D9 and D9.1: title and description may be empty while the listing is a DRAFT; they are required when it
 * goes to review. Registry data (brand, model, year, fuel) is prefilled from the vehicle and cannot be declared.
 * {@code currency} is optional (CRC or USD, default from configuration).
 */
public record CreateListingRequest(
        @NotNull UUID vehicleId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        @Pattern(regexp = "CRC|USD") String currency,
        @NotNull @Min(0) @Max(2000000) Integer mileage,
        @NotNull Province province,
        @NotNull Transmission transmission,
        @Size(max = 50) List<@Size(max = 80) String> declaredEquipment,
        @Size(max = 2000) String maintenanceHistory,
        Boolean negotiable,
        @Size(max = 120) String title,
        @Size(max = 1024) String description) {
}
