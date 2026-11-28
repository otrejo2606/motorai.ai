package ai.motoria.listing.dto;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.domain.PhotoAngleStatus;
import ai.motoria.listing.domain.PhotoAnomaly;
import ai.motoria.listing.domain.PhotoRejectionReason;
import ai.motoria.listing.domain.PhotoReviewDecision;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Photo in the analyst tray of the angle check (add-captura-guiada-fotos). Only for BACKOFFICE and ADMIN.
 *
 * @param angleReason   PhotoRejectionReason of a MISMATCH or PHOTO_CLASSIFIER_* code of an UNAVAILABLE check
 * @param readVin       VIN read in the photo, when the classifier service has OCR
 * @param readOdometerKm odometer read in the photo, when the classifier service has OCR
 */
public record PhotoReviewItemResponse(
        UUID listingId,
        ListingStatus listingStatus,
        UUID vehicleId,
        Integer declaredMileage,
        String slot,
        UUID mediaId,
        String publicUrl,
        PhotoAngleStatus angleStatus,
        String detectedSlot,
        BigDecimal angleConfidence,
        String angleReason,
        String angleModelVersion,
        String readVin,
        Integer readOdometerKm,
        PhotoAnomaly readingAnomaly,
        PhotoReviewDecision reviewDecision,
        PhotoRejectionReason reviewReason,
        Instant angleCheckedAt,
        Instant updatedAt) {
}
