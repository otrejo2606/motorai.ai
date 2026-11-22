package ai.motoria.listing.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AttachPhotoRequest(
        @NotNull UUID mediaId) {
}
