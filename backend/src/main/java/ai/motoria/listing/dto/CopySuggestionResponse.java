package ai.motoria.listing.dto;

import java.util.UUID;

/**
 * Suggestion only: the seller decides whether to copy it into the listing with PUT /listings/{id}.
 */
public record CopySuggestionResponse(
        UUID inferenceId,
        String title,
        String description) {
}
