package ai.motoria.listing.mapper;

import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingPhoto;
import ai.motoria.listing.dto.ListingPhotoResponse;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.ListingSummaryResponse;
import ai.motoria.listing.dto.ListingVehicleAttributeResponse;
import ai.motoria.listing.dto.ListingVehicleResponse;
import org.mapstruct.Mapper;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = "cdi")
public interface ListingMapper {

    String COVER_SLOT = "FRONT";

    /**
     * Registry attributes shown to the public (VIN and engine number are kept for the owner and the backoffice).
     */
    Set<String> PUBLIC_ATTRIBUTES = Set.of(
            "BRAND", "MODEL", "STYLE", "YEAR", "COLOR", "DISPLACEMENT_CC", "FUEL", "DRIVETRAIN", "DOORS", "CAPACITY");

    ListingPhotoResponse toPhotoResponse(ListingPhoto photo);

    /**
     * @param privileged owner or backoffice: includes seller id, price recommendation and private registry attributes
     */
    default ListingResponse toResponse(Listing listing, VehicleSnapshot vehicle, boolean privileged) {
        return new ListingResponse(
                listing.getId(),
                listing.getStatus(),
                listing.getTitle(),
                listing.getDescription(),
                listing.getCategory(),
                privileged ? listing.getSellerId() : null,
                listing.getVehicleId(),
                listing.getPrice(),
                listing.getCurrency(),
                privileged && listing.getRecommendedPriceRange() != null ? listing.getRecommendedPriceRange().getMin() : null,
                privileged && listing.getRecommendedPriceRange() != null ? listing.getRecommendedPriceRange().getMax() : null,
                listing.getModelYear(),
                listing.getMileage(),
                listing.getBrand(),
                listing.getModel(),
                listing.getFuel(),
                listing.getProvince(),
                listing.getTransmission(),
                listing.getTrustLevel(),
                listing.isNegotiable(),
                List.copyOf(listing.getDeclaredEquipment()),
                listing.getMaintenanceHistory(),
                vehicle == null ? null : toVehicleResponse(vehicle, privileged),
                listing.getPhotos().stream()
                        .sorted(Comparator.comparing(ListingPhoto::getSlot))
                        .map(this::toPhotoResponse)
                        .toList(),
                listing.getPublishedAt(),
                listing.getCreatedAt(),
                listing.getUpdatedAt());
    }

    default ListingVehicleResponse toVehicleResponse(VehicleSnapshot vehicle, boolean privileged) {
        List<ListingVehicleAttributeResponse> attributes = vehicle.attributes().stream()
                .filter(attribute -> privileged || PUBLIC_ATTRIBUTES.contains(attribute.key()))
                .map(attribute -> new ListingVehicleAttributeResponse(
                        attribute.key(), attribute.value(), attribute.source(), attribute.verified()))
                .toList();
        return new ListingVehicleResponse(
                vehicle.id(),
                vehicle.countryCode(),
                vehicle.status(),
                vehicle.transferBlocked(),
                vehicle.transferBlockReason(),
                vehicle.registryCheckedAt(),
                attributes);
    }

    default ListingSummaryResponse toSummary(Listing listing) {
        String cover = listing.findPhoto(COVER_SLOT)
                .or(() -> listing.getPhotos().stream().findFirst())
                .map(ListingPhoto::getPublicUrl)
                .orElse(null);
        return new ListingSummaryResponse(
                listing.getId(),
                listing.getStatus(),
                listing.getTitle(),
                listing.getPrice(),
                listing.getCurrency(),
                listing.getModelYear(),
                listing.getMileage(),
                listing.getBrand(),
                listing.getModel(),
                listing.getProvince(),
                listing.getTrustLevel(),
                cover,
                listing.getVehicleId());
    }
}
