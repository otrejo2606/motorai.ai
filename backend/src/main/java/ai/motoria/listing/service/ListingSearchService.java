package ai.motoria.listing.service;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.dto.PageResponse;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingFilter;
import ai.motoria.listing.dto.ListingSearchRequest;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Public search (design D9): only PUBLISHED listings, filters and sort, pages from 0.
 */
@ApplicationScoped
public class ListingSearchService {

    @Inject
    ListingRepository listingRepository;

    @Inject
    ListingMapper listingMapper;

    @Transactional
    public PageResponse<ListingSummaryResponse> search(ListingSearchRequest request) {
        int page = Math.max(request.page, 0);
        int size = Math.min(Math.max(request.size, 1), 50);
        ListingFilter filter = new ListingFilter(
                ListingStatus.PUBLISHED,
                request.brand,
                request.model,
                request.yearFrom,
                request.yearTo,
                request.minPrice,
                request.maxPrice,
                request.maxMileage,
                request.province,
                request.fuel,
                request.transmission,
                request.trustLevel,
                request.sort,
                page,
                size);

        PanacheQuery<Listing> query = listingRepository.search(filter);
        List<ListingSummaryResponse> items = query.list().stream()
                .map(listingMapper::toSummary)
                .toList();
        return new PageResponse<>(items, page, size, query.count());
    }
}
