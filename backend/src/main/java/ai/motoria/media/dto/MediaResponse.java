package ai.motoria.media.dto;

import ai.motoria.media.domain.MediaKind;
import ai.motoria.media.domain.MediaStatus;
import ai.motoria.media.domain.MediaVisibility;

import java.time.Instant;
import java.util.UUID;

public record MediaResponse(
        UUID id,
        MediaKind kind,
        String photoSlot,
        String contentType,
        Long sizeBytes,
        String sha256,
        Integer width,
        Integer height,
        MediaVisibility visibility,
        MediaStatus status,
        String publicUrl,
        Instant confirmedAt) {
}
