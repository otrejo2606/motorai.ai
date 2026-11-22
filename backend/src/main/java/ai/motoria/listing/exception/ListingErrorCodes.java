package ai.motoria.listing.exception;

/**
 * Stable error codes of the listing module (the frontend translates them). Publication rule codes are in
 * {@link ai.motoria.listing.domain.ListingPublicationPolicy}.
 */
public final class ListingErrorCodes {

    public static final String LISTING_NOT_FOUND = "LISTING_NOT_FOUND";
    public static final String LISTING_INVALID_STATE = "LISTING_INVALID_STATE";
    public static final String LISTING_ACCESS_DENIED = "LISTING_ACCESS_DENIED";
    public static final String LISTING_VEHICLE_NOT_FOUND = "LISTING_VEHICLE_NOT_FOUND";
    public static final String LISTING_ALREADY_EXISTS = "LISTING_ALREADY_EXISTS";
    public static final String LISTING_PHOTO_SLOT_UNKNOWN = "LISTING_PHOTO_SLOT_UNKNOWN";
    public static final String LISTING_PHOTO_INVALID = "LISTING_PHOTO_INVALID";
    public static final String LISTING_PHOTO_NOT_FOUND = "LISTING_PHOTO_NOT_FOUND";
    public static final String LISTING_PHOTO_CONFLICT = "LISTING_PHOTO_CONFLICT";

    private ListingErrorCodes() {
    }
}
