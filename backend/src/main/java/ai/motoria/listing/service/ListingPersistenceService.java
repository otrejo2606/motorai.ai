package ai.motoria.listing.service;

import ai.motoria.common.exception.BusinessRuleException;
import ai.motoria.common.exception.InvalidStateException;
import ai.motoria.common.persistence.UniqueViolations;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.listing.domain.Fuel;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingCategory;
import ai.motoria.listing.domain.PriceRange;
import ai.motoria.listing.domain.TrustLevel;
import ai.motoria.listing.dto.CreateListingRequest;
import ai.motoria.listing.dto.ListingResponse;
import ai.motoria.listing.dto.UpdateListingRequest;
import ai.motoria.listing.event.ListingEventProducer;
import ai.motoria.listing.exception.ListingErrorCodes;
import ai.motoria.listing.exception.ListingInvalidStateException;
import ai.motoria.listing.mapper.ListingMapper;
import ai.motoria.listing.repository.ListingRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;

@ApplicationScoped
class ListingPersistenceService {

    @Inject
    ListingRepository listingRepository;

    @Inject
    ListingMapper listingMapper;

    @Inject
    ListingEventProducer listingEventProducer;

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    ListingAccess listingAccess;

    @ConfigProperty(name = "motoria.listing.default-currency", defaultValue = "CRC")
    String defaultCurrency;

    /**
     * Creates the DRAFT listing prefilled with the vehicle registry data (task 6.2).
     */
    @Transactional
    public ListingResponse persistListing(CreateListingRequest request, UUID sellerId, PriceRange priceRange) {
        VehicleSnapshot vehicle = vehicleRegistryPort.findVehicle(request.vehicleId())
                .orElseThrow(() -> new BusinessRuleException(ListingErrorCodes.LISTING_VEHICLE_NOT_FOUND, "Vehicle not found"));
        if (!listingRepository.listActiveBySellerAndVehicle(sellerId, vehicle.id()).isEmpty()) {
            throw new InvalidStateException(ListingErrorCodes.LISTING_ALREADY_EXISTS,
                    "There is already an active listing for this vehicle");
        }
        Listing listing = Listing.create(
                sellerId,
                vehicle.id(),
                vehicle.countryCode(),
                ListingCategory.CAR,
                request.title(),
                request.description(),
                request.price(),
                currencyOf(request.currency()),
                request.mileage());
        listing.applyVehicleData(
                vehicle.attribute("BRAND").orElse(null),
                vehicle.attribute("MODEL").orElse(null),
                parseYear(vehicle.attribute("YEAR").orElse(null)),
                Fuel.fromRegistry(vehicle.attribute("FUEL").orElse(null)),
                vehicle.isRegistryVerified() ? TrustLevel.REGISTRY_VERIFIED : TrustLevel.NONE);
        listing.updateDeclaredData(
                request.title(),
                request.description(),
                request.price(),
                currencyOf(request.currency()),
                request.mileage(),
                request.province(),
                request.transmission(),
                request.declaredEquipment(),
                request.maintenanceHistory(),
                Boolean.TRUE.equals(request.negotiable()));
        listing.applyRecommendedPriceRange(priceRange);
        try {
            listingRepository.persistAndFlush(listing);
        } catch (PersistenceException exception) {
            if (UniqueViolations.isUniqueViolation(exception)) {
                throw new InvalidStateException(ListingErrorCodes.LISTING_ALREADY_EXISTS,
                        "There is already an active listing for this vehicle");
            }
            throw exception;
        }
        listingEventProducer.listingCreated(listing);
        return listingMapper.toResponse(listing, vehicle, true);
    }

    @Transactional
    public ListingResponse applyUpdate(UUID listingId, UpdateListingRequest request, PriceRange priceRange) {
        Listing listing = listingAccess.find(listingId);
        listingAccess.assertOwner(listing);
        if (!listing.isEditable()) {
            throw new ListingInvalidStateException("Only DRAFT listings can be updated");
        }
        listing.updateDeclaredData(
                request.title(),
                request.description(),
                request.price(),
                currencyOf(request.currency()),
                request.mileage(),
                request.province(),
                request.transmission(),
                request.declaredEquipment(),
                request.maintenanceHistory(),
                Boolean.TRUE.equals(request.negotiable()));
        listing.applyRecommendedPriceRange(priceRange);
        listingEventProducer.listingUpdated(listing);
        return listingMapper.toResponse(listing, vehicleRegistryPort.findVehicle(listing.getVehicleId()).orElse(null), true);
    }

    private String currencyOf(String requested) {
        return requested == null || requested.isBlank() ? defaultCurrency : requested;
    }

    static Integer parseYear(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
