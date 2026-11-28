package ai.motoria.user.dto;

import ai.motoria.user.domain.IdType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code idNumber} and {@code idType} are optional (design D9.1): when absent, the stored values are kept.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 200) String fullName,
        IdType idType,
        @Pattern(regexp = "[A-Za-z0-9 -]{5,30}") String idNumber,
        @Pattern(regexp = "[+0-9 -]{0,30}") String phone,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode) {
}
