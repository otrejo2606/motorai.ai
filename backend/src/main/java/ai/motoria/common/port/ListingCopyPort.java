package ai.motoria.common.port;

/**
 * Implemented by {@code ai} (use case LISTING_COPY). The suggestion is never applied automatically:
 * the seller must accept it.
 */
public interface ListingCopyPort {

    ListingCopySuggestion suggest(ListingCopyRequest request);
}
