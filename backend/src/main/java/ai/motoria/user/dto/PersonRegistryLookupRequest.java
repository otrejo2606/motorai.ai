package ai.motoria.user.dto;

import ai.motoria.user.domain.IdType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Backoffice lookup of a person by identity document. Sent in the body (not in the URL) so the number never
 * reaches access logs. {@code reason} is mandatory and logged with the analyst who asked.
 *
 * @param refresh true to pay a new query even if a fresh one exists
 */
public record PersonRegistryLookupRequest(
        @NotNull IdType idType,
        @NotBlank @Size(max = 30) String idNumber,
        @NotBlank @Size(min = 10, max = 300) String reason,
        Boolean refresh) {
}
