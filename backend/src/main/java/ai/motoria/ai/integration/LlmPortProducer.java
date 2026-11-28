package ai.motoria.ai.integration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Chooses the {@link LlmPort} adapter at runtime from motoria.ai.provider ("stub" or "openai-compatible").
 */
@ApplicationScoped
public class LlmPortProducer {

    @Produces
    @ApplicationScoped
    LlmPort llmPort(@ConfigProperty(name = "motoria.ai.provider", defaultValue = "stub") String provider,
                    HttpLlmAdapter httpLlmAdapter,
                    StubLlmAdapter stubLlmAdapter) {
        return StubLlmAdapter.PROVIDER.equalsIgnoreCase(provider) ? stubLlmAdapter : httpLlmAdapter;
    }
}
