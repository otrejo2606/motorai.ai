package ai.motoria.media.integration;

/**
 * Port to the local photo angle classifier (add-captura-guiada-fotos, design D3). The model is trained and served by
 * Motoria (owner gate 0.1); no external vision provider is used. Selected by motoria.media.angle-check.provider
 * ("stub" or "http"). Adapters never log the image.
 */
public interface PhotoAngleClassifierPort {

    /**
     * @param expectedSlot protocol slot the seller is filling (sent as context; the model predicts on its own)
     * @param image        JPEG without metadata, already reduced by the media module
     * @param mimeType     content type of {@code image}
     * @throws PhotoAngleClassifierUnavailableException when the classifier is not configured, fails or times out
     */
    PhotoAngleClassification classify(String expectedSlot, byte[] image, String mimeType);

    String providerName();
}
