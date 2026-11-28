package ai.motoria.listing.repository;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.domain.PhotoAngleStatus;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Persistence only. Fixed query with named parameters (no user text in the query).
 */
@ApplicationScoped
public class ListingPhotoRepository implements PanacheRepositoryBase<ListingPhoto, UUID> {

    static final List<PhotoAngleStatus> ANALYST_STATUSES = List.of(PhotoAngleStatus.UNCERTAIN, PhotoAngleStatus.UNAVAILABLE);
    static final List<ListingStatus> OPEN_LISTING_STATUSES = List.of(ListingStatus.DRAFT, ListingStatus.PENDING_REVIEW);
    private static final String AWAITING_ANALYST = """
            reviewDecision is null and (angleStatus in :statuses or readingAnomaly is not null)
            and listing.status in :listingStatuses and listing.sellerId <> :excludedSellerId""";

    /**
     * Analyst tray: photos of open listings (DRAFT or PENDING_REVIEW) that are UNCERTAIN, UNAVAILABLE or read something
     * that does not fit, without a decision, oldest first. The analyst never sees the listings they sell.
     */
    public PanacheQuery<ListingPhoto> findAwaitingAnalyst(UUID excludedSellerId, int page, int size) {
        return find(AWAITING_ANALYST,
                Sort.ascending("updatedAt"),
                Parameters.with("statuses", ANALYST_STATUSES)
                        .and("listingStatuses", OPEN_LISTING_STATUSES)
                        .and("excludedSellerId", excludedSellerId))
                .page(page, size);
    }
}
