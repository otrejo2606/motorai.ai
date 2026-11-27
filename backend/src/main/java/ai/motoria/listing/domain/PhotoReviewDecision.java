package ai.motoria.listing.domain;

/**
 * Decision of the analyst on a photo sent to review (UNCERTAIN, UNAVAILABLE, MISMATCH or with a reading anomaly).
 * ACCEPTED: the photo stays and does not block. REJECTED: the seller must repeat it; it blocks the review.
 */
public enum PhotoReviewDecision {
    ACCEPTED,
    REJECTED
}
