package ai.motoria.media.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSignatureVerifierTest {

    private final ContentSignatureVerifier verifier = new ContentSignatureVerifier();

    @Test
    void shouldRecognizeAllowedSignatures() {
        assertTrue(verifier.matches("application/pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(verifier.matches("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}));
        assertTrue(verifier.matches("image/png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0}));
        assertTrue(verifier.matches("image/heic", heic("heic")));
    }

    @Test
    void shouldRejectMismatchesAndUnknownTypes() {
        assertFalse(verifier.matches("application/pdf", "MZ executable".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(verifier.matches("image/jpeg", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(verifier.matches("image/heic", heic("isom")));
        assertFalse(verifier.matches("text/html", "<html>".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(verifier.matches("application/pdf", new byte[0]));
    }

    private byte[] heic(String brand) {
        byte[] content = new byte[16];
        byte[] ftyp = "ftyp".getBytes(StandardCharsets.US_ASCII);
        byte[] brandBytes = brand.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ftyp, 0, content, 4, 4);
        System.arraycopy(brandBytes, 0, content, 8, 4);
        return content;
    }
}
