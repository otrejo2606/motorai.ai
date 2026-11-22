package ai.motoria.listing.domain;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.common.persistence.AuditSubject;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Listing linked to a {@code Vehicle} (design D3). Registry data (brand, model, year, fuel) is copied from the
 * verified vehicle for search; the seller declares price, mileage, province, transmission, equipment, maintenance and
 * negotiation. Column names are explicit; V6 renamed the legacy lower-case columns.
 */
@Entity
@Table(name = "listing", schema = "listing")
public class Listing {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", nullable = false, length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ListingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private ListingCategory category;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Embedded
    private PriceRange recommendedPriceRange;

    @Column(name = "model_year")
    private Integer modelYear;

    @Column(name = "mileage", nullable = false)
    private Integer mileage;

    @Column(name = "brand", length = 80)
    private String brand;

    @Column(name = "model", length = 80)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel", length = 40)
    private Fuel fuel;

    @Enumerated(EnumType.STRING)
    @Column(name = "province", length = 40)
    private Province province;

    @Enumerated(EnumType.STRING)
    @Column(name = "transmission", length = 20)
    private Transmission transmission;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_level", nullable = false, length = 30)
    private TrustLevel trustLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "declared_equipment", nullable = false, columnDefinition = "jsonb")
    private List<String> declaredEquipment = new ArrayList<>();

    @Column(name = "maintenance_history", length = 2000)
    private String maintenanceHistory;

    @Column(name = "negotiable", nullable = false)
    private boolean negotiable;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 255)
    private String updatedBy;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<ListingPhoto> photos = new ArrayList<>();

    protected Listing() {
    }

    public static Listing create(UUID sellerId, UUID vehicleId, String countryCode, ListingCategory category,
                                 String title, String description, BigDecimal price, String currency, Integer mileage) {
        Listing listing = new Listing();
        listing.id = UUID.randomUUID();
        listing.status = ListingStatus.DRAFT;
        listing.sellerId = Objects.requireNonNull(sellerId);
        listing.vehicleId = Objects.requireNonNull(vehicleId);
        listing.countryCode = Objects.requireNonNull(countryCode);
        listing.category = Objects.requireNonNull(category);
        listing.title = title == null ? "" : title;
        listing.description = description == null ? "" : description;
        listing.price = price;
        listing.currency = currency;
        listing.mileage = mileage;
        listing.trustLevel = TrustLevel.NONE;
        listing.createdAt = Instant.now();
        listing.updatedAt = listing.createdAt;
        return listing;
    }

    @PrePersist
    void prePersist() {
        String subject = AuditSubject.current();
        if (createdBy == null) {
            createdBy = subject;
        }
        updatedBy = subject;
    }

    @PreUpdate
    void preUpdate() {
        updatedBy = AuditSubject.current();
    }

    /**
     * Prefill from the vehicle registry data (read only for the seller).
     */
    public void applyVehicleData(String brand, String model, Integer modelYear, Fuel fuel, TrustLevel trustLevel) {
        this.brand = brand;
        this.model = model;
        this.modelYear = modelYear;
        this.fuel = fuel;
        this.trustLevel = trustLevel == null ? TrustLevel.NONE : trustLevel;
        touch();
    }

    public void updateTrustLevel(TrustLevel trustLevel) {
        this.trustLevel = Objects.requireNonNull(trustLevel);
        touch();
    }

    public void updateDeclaredData(String title, String description, BigDecimal price, String currency, Integer mileage,
                                   Province province, Transmission transmission, List<String> declaredEquipment,
                                   String maintenanceHistory, boolean negotiable) {
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.price = price;
        this.currency = currency;
        this.mileage = mileage;
        this.province = province;
        this.transmission = transmission;
        this.declaredEquipment = declaredEquipment == null ? new ArrayList<>() : new ArrayList<>(declaredEquipment);
        this.maintenanceHistory = maintenanceHistory;
        this.negotiable = negotiable;
        touch();
    }

    public boolean isContentComplete() {
        return title != null && !title.isBlank() && description != null && !description.isBlank();
    }

    public boolean isEditable() {
        return status == ListingStatus.DRAFT;
    }

    public boolean isActive() {
        return status == ListingStatus.DRAFT || status == ListingStatus.PENDING_REVIEW || status == ListingStatus.PUBLISHED;
    }

    public void attachPhoto(String slot, UUID mediaId, String publicUrl) {
        Optional<ListingPhoto> existing = findPhoto(slot);
        if (existing.isPresent()) {
            existing.get().replace(mediaId, publicUrl);
        } else {
            photos.add(ListingPhoto.create(this, slot, mediaId, publicUrl));
        }
        touch();
    }

    public boolean removePhoto(String slot) {
        boolean removed = photos.removeIf(photo -> photo.getSlot().equals(slot));
        if (removed) {
            touch();
        }
        return removed;
    }

    public Optional<ListingPhoto> findPhoto(String slot) {
        return photos.stream().filter(photo -> photo.getSlot().equals(slot)).findFirst();
    }

    public List<String> photoSlots() {
        return photos.stream().map(ListingPhoto::getSlot).toList();
    }

    public void submitForReview() {
        ensureState(ListingStatus.DRAFT);
        this.status = ListingStatus.PENDING_REVIEW;
        touch();
    }

    public void publish() {
        ensureState(ListingStatus.PENDING_REVIEW);
        this.status = ListingStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        touch();
    }

    public void markSold() {
        ensureState(ListingStatus.PUBLISHED);
        this.status = ListingStatus.SOLD;
        touch();
    }

    public void applyRecommendedPriceRange(PriceRange recommendedPriceRange) {
        this.recommendedPriceRange = recommendedPriceRange;
        touch();
    }

    private void touch() {
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

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
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

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Fuel getFuel() {
        return fuel;
    }

    public Province getProvince() {
        return province;
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public TrustLevel getTrustLevel() {
        return trustLevel;
    }

    public List<String> getDeclaredEquipment() {
        return declaredEquipment;
    }

    public String getMaintenanceHistory() {
        return maintenanceHistory;
    }

    public boolean isNegotiable() {
        return negotiable;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public List<ListingPhoto> getPhotos() {
        return photos;
    }
}
