package ai.motoria.listing.domain;

import ai.motoria.common.port.OwnershipMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListingPublicationPolicyTest {

    private static final List<String> REQUIRED = List.of("FRONT", "DASHBOARD_ODOMETER", "VIN");

    @Test
    void completeListingShouldBePublishable() {
        ListingPublicationPolicy.Result result = ListingPublicationPolicy.evaluate(
                true, true, 2, OwnershipMatch.MATCH, REQUIRED, REQUIRED);

        assertTrue(result.publishable());
    }

    @Test
    void missingOdometerPhotoShouldBeReported() {
        ListingPublicationPolicy.Result result = ListingPublicationPolicy.evaluate(
                true, true, 2, OwnershipMatch.MATCH, REQUIRED, List.of("FRONT", "VIN"));

        assertEquals(List.of(ListingPublicationPolicy.PHOTOS_INCOMPLETE), result.violations());
        assertEquals(List.of("DASHBOARD_ODOMETER"), result.missingSlots());
    }

    @Test
    void unverifiedVehicleAndLevelOneIdentityShouldBeReported() {
        ListingPublicationPolicy.Result result = ListingPublicationPolicy.evaluate(
                true, false, 1, OwnershipMatch.UNKNOWN, REQUIRED, REQUIRED);

        assertEquals(List.of(ListingPublicationPolicy.VEHICLE_NOT_VERIFIED, ListingPublicationPolicy.IDENTITY_LEVEL_REQUIRED),
                result.violations());
    }

    @Test
    void sellerWhoIsNotTheOwnerCannotPublish() {
        ListingPublicationPolicy.Result result = ListingPublicationPolicy.evaluate(
                true, true, 2, OwnershipMatch.MISMATCH, REQUIRED, REQUIRED);

        assertEquals(List.of(ListingPublicationPolicy.SELLER_NOT_OWNER), result.violations());
    }

    @Test
    void emptyTitleShouldBlockReview() {
        ListingPublicationPolicy.Result result = ListingPublicationPolicy.evaluate(
                false, true, 2, OwnershipMatch.MATCH, REQUIRED, REQUIRED);

        assertEquals(List.of(ListingPublicationPolicy.CONTENT_INCOMPLETE), result.violations());
    }

    @Test
    void registryFuelShouldMapToCatalog() {
        assertEquals(Fuel.GASOLINE, Fuel.fromRegistry("GASOLINA"));
        assertEquals(Fuel.DIESEL, Fuel.fromRegistry("Diesel"));
        assertEquals(Fuel.PLUG_IN_HYBRID, Fuel.fromRegistry("HIBRIDO ENCHUFABLE"));
        assertEquals(Fuel.ELECTRIC, Fuel.fromRegistry("ELECTRICO"));
        assertEquals(Fuel.OTHER, Fuel.fromRegistry("HIDROGENO"));
    }
}
