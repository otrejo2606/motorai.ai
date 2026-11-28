package ai.motoria.media.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Chooses the {@link PhotoAngleClassifierPort} adapter from motoria.media.angle-check.provider. Only "http" selects
 * the inference service; any other value falls back to the stub, which never calls out.
 */
@ApplicationScoped
public class PhotoAngleClassifierPortProducer {

    @Produces
    @ApplicationScoped
    PhotoAngleClassifierPort photoAngleClassifierPort(
            @ConfigProperty(name = "motoria.media.angle-check.provider", defaultValue = "stub") String provider,
            HttpPhotoAngleClassifierAdapter httpAdapter,
            StubPhotoAngleClassifierAdapter stubAdapter) {
        return HttpPhotoAngleClassifierAdapter.PROVIDER.equalsIgnoreCase(provider.trim()) ? httpAdapter : stubAdapter;
    }
}
