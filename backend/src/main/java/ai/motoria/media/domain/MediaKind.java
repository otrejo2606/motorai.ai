package ai.motoria.media.domain;

/**
 * REGISTRY_PAGE_IMAGE is created by the server only: PNG render of a registry PDF page for the analyst view
 * (design D5: PDFs are never rendered in a third-party browser).
 */
public enum MediaKind {
    REGISTRY_DOCUMENT,
    LISTING_PHOTO,
    IDENTITY_DOCUMENT,
    REGISTRY_PAGE_IMAGE
}
