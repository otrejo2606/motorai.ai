package ai.motoria.listing.dto;

import ai.motoria.listing.domain.PhotoAngleStatus;
import ai.motoria.listing.domain.PhotoAnomaly;
import ai.motoria.listing.domain.PhotoReviewDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Photo of a slot. The angle check fields (add-captura-guiada-fotos) are only filled for the owner and the backoffice;
 * they are null for public viewers.
 *
 * @param angleStatus     NOT_CHECKED, PENDING, MATCH, MISMATCH, UNCERTAIN or UNAVAILABLE
 * @param detectedSlot    slot the classifier saw (a protocol slot or OTHER)
 * @param repeatReason    PhotoRejectionReason when the photo must be repeated; null otherwise
 * @param readingAnomaly  VIN_MISMATCH or ODOMETER_MISMATCH when the photo reads something that does not fit
 * @param reviewDecision  ACCEPTED or REJECTED by the analyst; null while undecided
 */
public record ListingPhotoResponse(
        String slot,
        UUID mediaId,
        String publicUrl,
        PhotoAngleStatus angleStatus,
        String detectedSlot,
        BigDecimal angleConfidence,
        boolean mustBeRepeated,
        String repeatReason,
        PhotoAnomaly readingAnomaly,
        PhotoReviewDecision reviewDecision,
        Instant angleCheckedAt) {
}
