package ai.motoria.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterVehicleRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @NotBlank @Size(max = 20) String plate) {
}
