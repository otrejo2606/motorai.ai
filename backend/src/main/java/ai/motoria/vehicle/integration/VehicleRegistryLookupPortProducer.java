package ai.motoria.vehicle.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Chooses the {@link VehicleRegistryLookupPort} adapter from motoria.vehicle.registry.provider ("stub" or "credid").
 * Any other value falls back to the stub so a typo never sends paid queries by accident.
 */
@ApplicationScoped
public class VehicleRegistryLookupPortProducer {

    @Produces
    @ApplicationScoped
    VehicleRegistryLookupPort vehicleRegistryLookupPort(
            @ConfigProperty(name = "motoria.vehicle.registry.provider", defaultValue = "stub") String provider,
            CredidVehicleRegistryAdapter credidAdapter,
            StubVehicleRegistryAdapter stubAdapter) {
        return CredidVehicleRegistryAdapter.PROVIDER.equalsIgnoreCase(provider.trim()) ? credidAdapter : stubAdapter;
    }
}
