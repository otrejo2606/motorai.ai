package ai.motoria.ai.integration;

import java.util.List;

/**
 * @param pageTexts       text of each processed page, index 0 is page 1
 * @param pngImagesBase64 page images, only when the PDF has (almost) no text layer
 */
public record PdfContent(
        int pageCount,
        List<String> pageTexts,
        List<String> pngImagesBase64) {
}
