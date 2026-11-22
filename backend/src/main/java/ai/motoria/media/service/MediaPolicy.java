package ai.motoria.media.service;

import ai.motoria.media.domain.MediaKind;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;

/**
 * Whitelist of content types and size limits per kind (design D5). Listing photos accept JPEG and PNG only:
 * the public copy is re-encoded without metadata and the JDK cannot decode HEIC; HEIC is accepted for identity
 * documents, which are never published.
 */
@ApplicationScoped
public class MediaPolicy {

    static final String PDF = "application/pdf";
    static final String JPEG = "image/jpeg";
    static final String PNG = "image/png";
    static final String HEIC = "image/heic";
    private static final long PAGE_IMAGE_MAX = 20L * 1024 * 1024;

    @ConfigProperty(name = "motoria.media.max-size.registry-document", defaultValue = "10485760")
    long maxRegistryDocument;

    @ConfigProperty(name = "motoria.media.max-size.identity-document", defaultValue = "10485760")
    long maxIdentityDocument;

    @ConfigProperty(name = "motoria.media.max-size.listing-photo", defaultValue = "15728640")
    long maxListingPhoto;

    @ConfigProperty(name = "motoria.media.photo.min-width", defaultValue = "1024")
    int minPhotoWidth;

    @ConfigProperty(name = "motoria.media.photo.min-height", defaultValue = "768")
    int minPhotoHeight;

    public Set<String> allowedContentTypes(MediaKind kind) {
        return switch (kind) {
            case REGISTRY_DOCUMENT -> Set.of(PDF);
            case LISTING_PHOTO -> Set.of(JPEG, PNG);
            case IDENTITY_DOCUMENT -> Set.of(JPEG, PNG, HEIC);
            case REGISTRY_PAGE_IMAGE -> Set.of(PNG);
        };
    }

    public long maxSize(MediaKind kind) {
        return switch (kind) {
            case REGISTRY_DOCUMENT -> maxRegistryDocument;
            case LISTING_PHOTO -> maxListingPhoto;
            case IDENTITY_DOCUMENT -> maxIdentityDocument;
            case REGISTRY_PAGE_IMAGE -> PAGE_IMAGE_MAX;
        };
    }

    public boolean isDecodableImage(String contentType) {
        return JPEG.equals(contentType) || PNG.equals(contentType);
    }

    public boolean meetsMinimumResolution(int width, int height) {
        int longSide = Math.max(width, height);
        int shortSide = Math.min(width, height);
        return longSide >= Math.max(minPhotoWidth, minPhotoHeight) && shortSide >= Math.min(minPhotoWidth, minPhotoHeight);
    }
}
