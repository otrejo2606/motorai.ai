package ai.motoria.vehicle.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FulfillRegistryRequestRequest(
        @NotNull UUID mediaId) {
}
