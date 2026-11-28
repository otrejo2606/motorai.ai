package ai.motoria.media.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdatePhotoSlotRequest(
        @NotNull Boolean required,
        @NotNull @Min(1) @Max(99) Integer order,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._]{1,120}") String instructionKey) {
}
