package ai.motoria.media.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Checks the file signature (magic bytes), not the extension or the declared Content-Type.
 */
@ApplicationScoped
public class ContentSignatureVerifier {

    private static final int[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8, 0xFF};
    private static final int[] PNG_SIGNATURE = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] FTYP = {0x66, 0x74, 0x79, 0x70};
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1");

    public boolean matches(String contentType, byte[] content) {
        if (contentType == null || content == null) {
            return false;
        }
        return switch (contentType) {
            case MediaPolicy.PDF -> startsWith(content, 0, PDF_SIGNATURE);
            case MediaPolicy.JPEG -> startsWith(content, 0, JPEG_SIGNATURE);
            case MediaPolicy.PNG -> startsWith(content, 0, PNG_SIGNATURE);
            case MediaPolicy.HEIC -> isHeic(content);
            default -> false;
        };
    }

    private boolean isHeic(byte[] content) {
        if (content.length < 12 || !startsWith(content, 4, FTYP)) {
            return false;
        }
        String brand = new String(content, 8, 4, StandardCharsets.US_ASCII);
        return HEIC_BRANDS.contains(brand);
    }

    private boolean startsWith(byte[] content, int offset, int[] signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((content[offset + index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
