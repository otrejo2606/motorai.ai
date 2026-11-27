package ai.motoria.listing.dto;

import ai.motoria.listing.domain.PhotoRejectionReason;
import ai.motoria.listing.domain.PhotoReviewDecision;
import jakarta.validation.constraints.NotNull;

/**
 * Decision of the analyst on a photo. {@code reason} is required when the decision is REJECTED.
 */
public record PhotoReviewRequest(
        @NotNull PhotoReviewDecision decision,
        PhotoRejectionReason reason) {
}
