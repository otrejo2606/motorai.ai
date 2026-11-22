package ai.motoria.media.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The client PUTs the file to {@code uploadUrl} with the same Content-Type and size, then calls confirm.
 */
public record UploadTicketResponse(
        UUID mediaId,
        String uploadUrl,
        Instant expiresAt) {
}
