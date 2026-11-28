package ai.motoria.vehicle.integration;

import ai.motoria.vehicle.domain.VehicleAttributeKey;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.EnumMap;
import java.util.Map;

/**
 * Deterministic vehicle registry for dev and tests (motoria.vehicle.registry.provider=stub). It never calls out:
 * a plate ending in 000 is not found, one ending in 999 has a pledge, any other returns complete fictitious data whose
 * owner is motoria.vehicle.registry.stub-owner-name with the fictitious number motoria.vehicle.registry.stub-owner-id
 * (set it to a test seller's number to see the ownership match).
 */
@ApplicationScoped
@Typed(StubVehicleRegistryAdapter.class)
public class StubVehicleRegistryAdapter implements VehicleRegistryLookupPort {

    public static final String PROVIDER = "stub";
    static final String PLEDGE_AMOUNT = "5000000";

    private final String ownerName;
    private final String ownerIdNumber;

    @Inject
    public StubVehicleRegistryAdapter(
            @ConfigProperty(name = "motoria.vehicle.registry.stub-owner-name", defaultValue = "PROPIETARIO DE PRUEBA MOTORIA")
            String ownerName,
            @ConfigProperty(name = "motoria.vehicle.registry.stub-owner-id", defaultValue = "100000001")
            String ownerIdNumber) {
        this.ownerName = ownerName;
        this.ownerIdNumber = ownerIdNumber;
    }

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public VehicleRegistryRecord lookup(String normalizedPlate) {
        if (normalizedPlate.endsWith("000")) {
            return VehicleRegistryRecord.notFound("STUB");
        }
        boolean pledged = normalizedPlate.endsWith("999");
        Map<VehicleAttributeKey, String> attributes = new EnumMap<>(VehicleAttributeKey.class);
        attributes.put(VehicleAttributeKey.BRAND, "TOYOTA");
        attributes.put(VehicleAttributeKey.MODEL, "COROLLA");
        attributes.put(VehicleAttributeKey.YEAR, "2019");
        attributes.put(VehicleAttributeKey.COLOR, "GRIS");
        attributes.put(VehicleAttributeKey.VIN, "STUBVIN" + normalizedPlate);
        attributes.put(VehicleAttributeKey.ENGINE_NUMBER, "STUBMOTOR" + normalizedPlate);
        attributes.put(VehicleAttributeKey.DISPLACEMENT_CC, "1800");
        attributes.put(VehicleAttributeKey.FUEL, "GASOLINA");
        attributes.put(VehicleAttributeKey.DRIVETRAIN, "4X2");
        attributes.put(VehicleAttributeKey.CAPACITY, "5");
        return new VehicleRegistryRecord(true, attributes, pledged, pledged ? PLEDGE_AMOUNT : null,
                ownerName, ownerIdNumber, "STUB");
    }
}
