package ai.motoria.user.dto;

import ai.motoria.user.domain.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ConsentRequest(
        @NotNull ConsentType type,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,20}") String textVersion,
        @NotNull Boolean granted) {
}
