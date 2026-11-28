package ai.motoria.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * For OWNER_ID_HASH the analyst sends the owner's identity number in clear; it is stored only as its keyed hash.
 */
public record CorrectAttributeRequest(
        @NotBlank @Size(max = 500) String value) {
}
