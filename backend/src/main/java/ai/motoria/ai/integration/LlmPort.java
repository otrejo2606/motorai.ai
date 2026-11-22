package ai.motoria.ai.integration;

/**
 * Port to the language model, selected by configuration (motoria.ai.provider). Adapters always use temperature 0
 * when the provider accepts it and never log prompts or outputs.
 */
public interface LlmPort {

    LlmResponse complete(LlmRequest request);
}
