package ai.motoria.listing.service;

import ai.motoria.common.audit.Audited;
import ai.motoria.common.port.ListingCopyPort;
import ai.motoria.common.port.ListingCopyRequest;
import ai.motoria.common.port.ListingCopySuggestion;
import ai.motoria.common.port.VehicleAttributeSnapshot;
import ai.motoria.common.port.VehicleRegistryPort;
import ai.motoria.common.port.VehicleSnapshot;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.dto.CopySuggestionResponse;
import ai.motoria.listing.mapper.ListingMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Title and description assistant (task 4.4): only verified public registry data and the seller's declared data are
 * sent to the model. The result is a suggestion; nothing is written to the listing.
 */
@ApplicationScoped
public class ListingCopyAssistService {

    @Inject
    ListingAccess listingAccess;

    @Inject
    VehicleRegistryPort vehicleRegistryPort;

    @Inject
    ListingCopyPort listingCopyPort;

    @Audited
    public CopySuggestionResponse suggest(UUID listingId, String language) {
        Listing listing = listingAccess.find(listingId);
        listingAccess.assertOwner(listing);
        Map<String, String> registry = new LinkedHashMap<>();
        vehicleRegistryPort.findVehicle(listing.getVehicleId())
                .map(VehicleSnapshot::attributes)
                .ifPresent(attributes -> {
                    for (VehicleAttributeSnapshot attribute : attributes) {
                        if (attribute.verified() && ListingMapper.PUBLIC_ATTRIBUTES.contains(attribute.key())) {
                            registry.put(attribute.key(), attribute.value());
                        }
                    }
                });
        ListingCopySuggestion suggestion = listingCopyPort.suggest(new ListingCopyRequest(
                listing.getId(),
                language,
                registry,
                listing.getMileage(),
                listing.getTransmission() == null ? null : listing.getTransmission().name(),
                listing.getProvince() == null ? null : listing.getProvince().name(),
                listing.getDeclaredEquipment(),
                listing.getMaintenanceHistory(),
                listing.isNegotiable(),
                listing.getPrice()));
        return new CopySuggestionResponse(suggestion.inferenceId(), suggestion.title(), suggestion.description());
    }
}
