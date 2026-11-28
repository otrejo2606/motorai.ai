package ai.motoria.media.dto;

import ai.motoria.media.domain.MediaKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUploadRequest(
        @NotNull MediaKind kind,
        @NotBlank @Size(max = 60) String contentType,
        @NotNull @Positive Long sizeBytes,
        @Pattern(regexp = "[A-Z_]{2,30}") String photoSlot) {
}
