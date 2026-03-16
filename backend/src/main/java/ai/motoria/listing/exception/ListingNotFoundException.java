package ai.motoria.listing.exception;

import ai.motoria.common.exception.NotFoundException;

import java.util.UUID;

public class ListingNotFoundException extends NotFoundException {

    public ListingNotFoundException(UUID listingId) {
        super("Listing " + listingId + " was not found");
    }
}