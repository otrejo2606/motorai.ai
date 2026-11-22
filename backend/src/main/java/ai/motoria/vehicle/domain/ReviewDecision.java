package ai.motoria.vehicle.domain;

/**
 * Outcome of evaluating an extraction.
 *
 * @param applyData false when nothing may be applied (plate mismatch, failed extraction)
 * @param reason    why the case goes to the analyst; null means automatic approval
 */
public record ReviewDecision(
        boolean applyData,
        ReviewReason reason) {

    public boolean autoApprove() {
        return reason == null;
    }
}
