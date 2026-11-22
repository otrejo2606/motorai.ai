package ai.motoria.ai.integration;

import ai.motoria.ai.domain.AiUseCase;

import java.util.List;

/**
 * @param systemPrompt     fixed instructions (never contains user or document text)
 * @param userContent      JSON document with the data; untrusted text only travels here
 * @param pngImagesBase64  page images when the PDF has no text layer
 * @param jsonOutput       ask the provider for a JSON object response
 */
public record LlmRequest(
        AiUseCase useCase,
        String systemPrompt,
        String userContent,
        List<String> pngImagesBase64,
        int maxOutputTokens,
        boolean jsonOutput) {
}
