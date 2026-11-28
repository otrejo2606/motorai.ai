package ai.motoria.listing.exception;

import ai.motoria.common.exception.InvalidStateException;

public class ListingInvalidStateException extends InvalidStateException {

    public ListingInvalidStateException(String message) {
        super(ListingErrorCodes.LISTING_INVALID_STATE, message);
    }
}
