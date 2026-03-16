package ai.motoria.listing.domain;

import ai.motoria.common.domain.ListingStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "listing", schema = "listing")
public class Listing {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingCategory category;

    @Column(nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private UUID vehicleSpecId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Embedded
    private PriceRange recommendedPriceRange;

    @Column(nullable = false)
    private Integer modelYear;

    @Column(nullable = false)
    private Integer mileage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public static Listing create(UUID sellerId, UUID vehicleSpecId, ListingCategory category, String title,
                                 String description, BigDecimal price, Integer modelYear, Integer mileage) {
        Listing listing = new Listing();
        listing.id = UUID.randomUUID();
        listing.status = ListingStatus.DRAFT;
        listing.sellerId = sellerId;
        listing.vehicleSpecId = vehicleSpecId;
        listing.category = category;
        listing.title = title;
        listing.description = description;
        listing.price = price;
        listing.modelYear = modelYear;
        listing.mileage = mileage;
        listing.createdAt = Instant.now();
        listing.updatedAt = listing.createdAt;
        return listing;
    }

    private void initialize() {
        this.id = UUID.randomUUID();
        this.status = ListingStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            initialize();
        }
    }

    public void updateDetails(String title, String description, BigDecimal price, Integer modelYear, Integer mileage) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.modelYear = modelYear;
        this.mileage = mileage;
        this.updatedAt = Instant.now();
    }

    public void submitForReview() {
        ensureState(ListingStatus.DRAFT);
        this.status = ListingStatus.PENDING_REVIEW;
        this.updatedAt = Instant.now();
    }

    public void publish() {
        ensureState(ListingStatus.PENDING_REVIEW);
        this.status = ListingStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    public void markSold() {
        ensureState(ListingStatus.PUBLISHED);
        this.status = ListingStatus.SOLD;
        this.updatedAt = Instant.now();
    }

    public void applyRecommendedPriceRange(PriceRange recommendedPriceRange) {
        this.recommendedPriceRange = recommendedPriceRange;
        this.updatedAt = Instant.now();
    }

    private void ensureState(ListingStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException("Listing must be in state " + expected + " but was " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public ListingCategory getCategory() {
        return category;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public UUID getVehicleSpecId() {
        return vehicleSpecId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public PriceRange getRecommendedPriceRange() {
        return recommendedPriceRange;
    }

    public Integer getModelYear() {
        return modelYear;
    }

    public Integer getMileage() {
        return mileage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}