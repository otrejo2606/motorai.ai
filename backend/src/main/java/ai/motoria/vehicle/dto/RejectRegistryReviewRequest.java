package ai.motoria.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRegistryReviewRequest(
        @NotBlank @Size(max = 500) String reason) {
}
