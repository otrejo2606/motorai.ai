package ai.motoria.listing.domain;

/**
 * Angle check of a listing photo (add-captura-guiada-fotos). NOT_CHECKED: check off or slot not configured (and rows
 * older than V10). PENDING: attached, the check has not finished. MATCH: the photo shows the slot. MISMATCH: it shows
 * something else; the seller must repeat it and it blocks the review. UNCERTAIN: the analyst decides. UNAVAILABLE: the
 * classifier failed; it does not block and the analyst reviews it.
 */
public enum PhotoAngleStatus {
    NOT_CHECKED,
    PENDING,
    MATCH,
    MISMATCH,
    UNCERTAIN,
    UNAVAILABLE;

    /**
     * Unknown values (a newer classifier answer) are treated as UNCERTAIN, so the analyst sees them.
     */
    public static PhotoAngleStatus fromCheck(String value) {
        if (value == null) {
            return UNAVAILABLE;
        }
        for (PhotoAngleStatus status : values()) {
            if (status.name().equals(value)) {
                return status == PENDING ? UNCERTAIN : status;
            }
        }
        return UNCERTAIN;
    }
}
