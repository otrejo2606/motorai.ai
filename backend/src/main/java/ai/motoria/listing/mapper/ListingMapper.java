package ai.motoria.listing.mapper;

import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.ListingSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "cdi")
public interface ListingMapper {

    @Mapping(target = "recommendedPriceMin", source = "recommendedPriceRange.min")
    @Mapping(target = "recommendedPriceMax", source = "recommendedPriceRange.max")
    ListingResponse toResponse(Listing listing);

    ListingSummaryResponse toSummary(Listing listing);
}