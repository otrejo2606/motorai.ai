package ai.motoria.listing.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.exception.NotFoundException;
import ai.motoria.common.port.MediaPort;
import ai.motoria.common.port.PhotoAngleCheckResult;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.mapper.ListingMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.UUID;

/**
 * Photos by protocol slot (design D9 of F1): only confirmed LISTING_PHOTO media of the seller, with its public copy.
 * Since add-captura-guiada-fotos every attached photo is checked by the local angle classifier in the same request
 * (design D2): attach (transaction), classifier (no transaction), result (transaction). The answer already carries the
 * result for the portal.
 */
@ApplicationScoped
public class ListingPhotoService {

    @Inject
    ListingPhotoAngleApplier angleApplier;

    @Inject
    MediaPort mediaPort;

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    ListingMapper listingMapper;

    /**
     * Not transactional on purpose: the classifier call must never run inside a database transaction.
     */
    @Audited
    public ListingResponse attach(UUID listingId, String slot, UUID mediaId) {
        angleApplier.attach(listingId, slot, mediaId);
        PhotoAngleCheckResult result;
        try {
            result = mediaPort.checkPhotoAngle(mediaId, slot);
        } catch (RuntimeException exception) {
            Log.warnv("Angle check of listing {0} failed: {1}", listingId, exception.getClass().getSimpleName());
            result = PhotoAngleCheckResult.unavailable(PhotoAngleCheckResult.ERROR_UNAVAILABLE, null);
        }
        return angleApplier.apply(listingId, slot, mediaId, result);
    }

    @Audited
    @Transactional
    public ListingResponse remove(UUID listingId, String slot) {
        Listing listing = angleApplier.editableListing(listingId);
        if (!listing.removePhoto(slot)) {
            throw new NotFoundException(ListingErrorCodes.LISTING_PHOTO_NOT_FOUND, "No photo in this slot");
        }
        return listingMapper.toResponse(listing, vehicleRegistryPort.findVehicle(listing.getVehicleId()).orElse(null), true);
    }
}
