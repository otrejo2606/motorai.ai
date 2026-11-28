package ai.motoria.listing.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.MediaSnapshot;
import ai.motoria.common.port.PhotoAngleCheckResult;
import ai.motoria.common.port.PhotoSlotSnapshot;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.domain.PhotoAngleStatus;
import ai.motoria.listing.domain.PhotoRejectionReason;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional steps around the (non transactional) angle check of a listing photo, like
 * VehicleRegistryLookupApplier: {@link #attach} stores the photo as PENDING, {@link #apply} stores the result. The
 * classifier call happens between both, without a database transaction.
 */
@ApplicationScoped
public class ListingPhotoAngleApplier {

    static final String LISTING_PHOTO_KIND = "LISTING_PHOTO";

    @Inject
    ListingAccess listingAccess;

    @Inject
    MediaPort mediaPort;

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    ListingRepository listingRepository;

    @Inject
    ListingMapper listingMapper;

    @Inject
    ListingPhotoReadingChecker readingChecker;

    /**
     * Only confirmed LISTING_PHOTO media of the seller, with its public copy, for a slot of the protocol (design D9 of
     * F1). The photo starts PENDING.
     */
    @Transactional
    public void attach(UUID listingId, String slot, UUID mediaId) {
        Listing listing = editableListing(listingId);
        boolean knownSlot = mediaPort.photoProtocol(listing.getCountryCode()).stream()
                .map(PhotoSlotSnapshot::code)
                .anyMatch(code -> code.equals(slot));
        if (!knownSlot) {
            throw new InvalidRequestException(ListingErrorCodes.LISTING_PHOTO_SLOT_UNKNOWN, "Unknown photo slot");
        }
        MediaSnapshot media = mediaPort.findMedia(mediaId)
                .orElseThrow(() -> new BusinessRuleException(ListingErrorCodes.LISTING_PHOTO_INVALID, "Unknown photo"));
        boolean valid = media.ownerId().equals(listing.getSellerId())
                && LISTING_PHOTO_KIND.equals(media.kind())
                && media.isConfirmed()
                && media.publicUrl() != null
                && (media.photoSlot() == null || media.photoSlot().equals(slot));
        if (!valid) {
            throw new BusinessRuleException(ListingErrorCodes.LISTING_PHOTO_INVALID,
                    "The photo must be a confirmed listing photo of the seller for this slot");
        }
        listing.attachPhoto(slot, media.id(), media.publicUrl());
        try {
            listingRepository.flush();
        } catch (PersistenceException exception) {
            if (UniqueViolations.isUniqueViolation(exception)) {
                throw new InvalidStateException(ListingErrorCodes.LISTING_PHOTO_CONFLICT,
                        "Another photo was saved in this slot at the same time; reload and try again");
            }
            throw exception;
        }
    }

    /**
     * Stores the result if the slot still holds the checked photo and is still PENDING (otherwise it is ignored: a newer
     * photo has its own check), compares what the photo reads with the vehicle and returns the listing for the seller.
     */
    @Transactional
    public ListingResponse apply(UUID listingId, String slot, UUID mediaId, PhotoAngleCheckResult result) {
        Listing listing = listingAccess.find(listingId);
        Optional<ListingPhoto> photo = listing.findPhoto(slot);
        if (photo.isPresent()) {
            PhotoAngleStatus status = PhotoAngleStatus.fromCheck(result.status());
            boolean applied = photo.get().applyAngleCheck(mediaId, status, result.predictedSlot(), result.confidence(),
                    result.modelVersion(), result.latencyMs(), reasonOf(status, result), result.vin(),
                    result.odometerKm(), Instant.now());
            if (applied && (result.vin() != null || result.odometerKm() != null)) {
                readingChecker.check(listing, vehicleRegistryPort.findVehicle(listing.getVehicleId()).orElse(null));
            }
        }
        return listingMapper.toResponse(listing, vehicleRegistryPort.findVehicle(listing.getVehicleId()).orElse(null), true);
    }

    /**
     * MISMATCH: NOT_THE_VEHICLE when the classifier sees no vehicle view (OTHER), otherwise WRONG_ANGLE.
     * UNAVAILABLE: the stable classifier code (for the analyst; the seller is not blocked).
     */
    static String reasonOf(PhotoAngleStatus status, PhotoAngleCheckResult result) {
        if (status == PhotoAngleStatus.MISMATCH) {
            return PhotoAngleCheckResult.OTHER_SLOT.equals(result.predictedSlot())
                    ? PhotoRejectionReason.NOT_THE_VEHICLE.name()
                    : PhotoRejectionReason.WRONG_ANGLE.name();
        }
        if (status == PhotoAngleStatus.UNAVAILABLE) {
            return result.errorCode();
        }
        return null;
    }

    /**
     * Only the seller who owns the listing (or an ADMIN) changes its photos, and only while it is a DRAFT. Caller must be
     * inside a transaction.
     */
    public Listing editableListing(UUID listingId) {
        Listing listing = listingAccess.find(listingId);
        listingAccess.assertOwner(listing);
        if (!listing.isEditable()) {
            throw new ListingInvalidStateException("Photos can only change while the listing is a DRAFT");
        }
        return listing;
    }
}
