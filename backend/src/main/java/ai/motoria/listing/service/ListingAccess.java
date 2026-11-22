package ai.motoria.listing.service;

import ai.motoria.common.exception.ForbiddenOperationException;
import ai.motoria.common.security.CurrentUser;
import ai.motoria.common.security.Roles;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.exception.ListingNotFoundException;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Ownership checks shared by the listing services.
 */
@ApplicationScoped
public class ListingAccess {

    @Inject
    ListingRepository listingRepository;

    @Inject
    CurrentUser currentUser;

    public Listing find(UUID listingId) {
        return listingRepository.findByIdOptional(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
    }

    public boolean isOwner(Listing listing) {
        return currentUser.optionalId().map(id -> id.equals(listing.getSellerId())).orElse(false);
    }

    public boolean isPrivilegedViewer(Listing listing) {
        return isOwner(listing) || currentUser.isBackoffice();
    }

    /**
     * Only the seller who owns the listing (or an ADMIN) may change it.
     */
    public void assertOwner(Listing listing) {
        if (!isOwner(listing) && !currentUser.hasRole(Roles.ADMIN)) {
            throw new ForbiddenOperationException(ListingErrorCodes.LISTING_ACCESS_DENIED,
                    "Access denied to listing " + listing.getId());
        }
    }
}
