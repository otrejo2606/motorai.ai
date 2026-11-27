package ai.motoria.user.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Chooses the {@link PersonRegistryPort} adapter from motoria.user.person-registry.provider ("stub" or "credid").
 * Any other value falls back to the stub so a typo never sends real queries by accident.
 */
@ApplicationScoped
public class PersonRegistryPortProducer {

    @Produces
    @ApplicationScoped
    PersonRegistryPort personRegistryPort(
            @ConfigProperty(name = "motoria.user.person-registry.provider", defaultValue = "stub") String provider,
            CredidPersonRegistryAdapter credidAdapter,
            StubPersonRegistryAdapter stubAdapter) {
        return CredidPersonRegistryAdapter.PROVIDER.equalsIgnoreCase(provider.trim()) ? credidAdapter : stubAdapter;
    }
}
