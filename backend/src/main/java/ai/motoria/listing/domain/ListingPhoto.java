package ai.motoria.listing.domain;

import ai.motoria.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Photo of a protocol slot. {@code publicUrl} points to the metadata-free public copy; the original stays private.
 */
@Entity
@Table(name = "listing_photo", schema = "listing")
public class ListingPhoto extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "slot", nullable = false, length = 30)
    private String slot;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

    @Column(name = "public_url", nullable = false, length = 1000)
    private String publicUrl;

    protected ListingPhoto() {
    }

    static ListingPhoto create(Listing listing, String slot, UUID mediaId, String publicUrl) {
        ListingPhoto photo = new ListingPhoto();
        photo.id = UUID.randomUUID();
        photo.listing = Objects.requireNonNull(listing);
        photo.slot = Objects.requireNonNull(slot);
        photo.replace(mediaId, publicUrl);
        return photo;
    }

    void replace(UUID mediaId, String publicUrl) {
        this.mediaId = Objects.requireNonNull(mediaId);
        this.publicUrl = Objects.requireNonNull(publicUrl);
    }

    public UUID getId() {
        return id;
    }

    public String getSlot() {
        return slot;
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public String getPublicUrl() {
        return publicUrl;
    }
}
