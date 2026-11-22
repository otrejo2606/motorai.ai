package ai.motoria.common.port;

import java.util.UUID;

public record ListingCopySuggestion(
        UUID inferenceId,
        String title,
        String description) {
}
