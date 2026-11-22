package ai.motoria.listing.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidRequestException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.MediaSnapshot;
import ai.motoria.common.port.PhotoSlotSnapshot;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Photos by protocol slot (design D9): only confirmed LISTING_PHOTO media of the seller, with its public copy.
 */
@ApplicationScoped
public class ListingPhotoService {

    static final String LISTING_PHOTO_KIND = "LISTING_PHOTO";

    @Inject
    ListingAccess listingAccess;

    @Inject
    MediaPort mediaPort;

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    ListingMapper listingMapper;

    @Inject
    ListingRepository listingRepository;

    @Audited
    @Transactional
    public ListingResponse attach(UUID listingId, String slot, UUID mediaId) {
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
        return view(listing);
    }

    @Audited
    @Transactional
    public ListingResponse remove(UUID listingId, String slot) {
        Listing listing = editableListing(listingId);
        if (!listing.removePhoto(slot)) {
            throw new NotFoundException(ListingErrorCodes.LISTING_PHOTO_NOT_FOUND, "No photo in this slot");
        }
        return view(listing);
    }

    private Listing editableListing(UUID listingId) {
        Listing listing = listingAccess.find(listingId);
        listingAccess.assertOwner(listing);
        if (!listing.isEditable()) {
            throw new ListingInvalidStateException("Photos can only change while the listing is a DRAFT");
        }
        return listing;
    }

    private ListingResponse view(Listing listing) {
        return listingMapper.toResponse(listing, vehicleRegistryPort.findVehicle(listing.getVehicleId()).orElse(null), true);
    }
}
