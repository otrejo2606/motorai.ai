package ai.motoria.listing.service;

import ai.motoria.listing.domain.ListingFilter;
import ai.motoria.listing.dto.ListingSearchRequest;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class ListingSearchService {

    @Inject
    ListingRepository listingRepository;

    @Inject
    ListingMapper listingMapper;

    public List<ListingSummaryResponse> search(ListingSearchRequest request) {
        int page = Math.max(request.page, 0);
        int size = Math.min(Math.max(request.size, 1), 50);
        ListingFilter filter = new ListingFilter(
                request.status,
                request.category,
                request.minPrice,
                request.maxPrice,
                request.modelYear,
                page,
                size);

        return listingRepository.search(filter)
                .stream()
                .map(listingMapper::toSummary)
                .toList();
    }
}