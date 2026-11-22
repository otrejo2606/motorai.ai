package ai.motoria.listing.repository;

import ai.motoria.common.domain.ListingStatus;
import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingFilter;
import ai.motoria.listing.domain.ListingSort;
import ai.motoria.listing.domain.TrustLevel;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence only. Queries are built from fixed fragments with named parameters (no user text in the query).
 */
@ApplicationScoped
public class ListingRepository implements PanacheRepositoryBase<Listing, UUID> {

    public PanacheQuery<Listing> search(ListingFilter filter) {
        StringBuilder query = new StringBuilder("status = :status");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("status", filter.status());

        if (filter.brand() != null && !filter.brand().isBlank()) {
            query.append(" and lower(brand) = :brand");
            parameters.put("brand", filter.brand().trim().toLowerCase(Locale.ROOT));
        }
        if (filter.model() != null && !filter.model().isBlank()) {
            query.append(" and lower(model) = :model");
            parameters.put("model", filter.model().trim().toLowerCase(Locale.ROOT));
        }
        if (filter.yearFrom() != null) {
            query.append(" and modelYear >= :yearFrom");
            parameters.put("yearFrom", filter.yearFrom());
        }
        if (filter.yearTo() != null) {
            query.append(" and modelYear <= :yearTo");
            parameters.put("yearTo", filter.yearTo());
        }
        if (filter.minPrice() != null) {
            query.append(" and price >= :minPrice");
            parameters.put("minPrice", filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            query.append(" and price <= :maxPrice");
            parameters.put("maxPrice", filter.maxPrice());
        }
        if (filter.maxMileage() != null) {
            query.append(" and mileage <= :maxMileage");
            parameters.put("maxMileage", filter.maxMileage());
        }
        if (filter.province() != null) {
            query.append(" and province = :province");
            parameters.put("province", filter.province());
        }
        if (filter.fuel() != null) {
            query.append(" and fuel = :fuel");
            parameters.put("fuel", filter.fuel());
        }
        if (filter.transmission() != null) {
            query.append(" and transmission = :transmission");
            parameters.put("transmission", filter.transmission());
        }
        if (filter.trustLevel() != null) {
            query.append(" and trustLevel = :trustLevel");
            parameters.put("trustLevel", filter.trustLevel());
        }
        return find(query.toString(), sortOf(filter.sort()), parameters)
                .page(filter.page(), filter.size());
    }

    public PanacheQuery<Listing> findBySeller(UUID sellerId, int page, int size) {
        return find("sellerId", Sort.descending("updatedAt"), sellerId).page(page, size);
    }

    public PanacheQuery<Listing> findByStatus(ListingStatus status, int page, int size) {
        return find("status", Sort.ascending("updatedAt"), status).page(page, size);
    }

    public List<Listing> listActiveBySellerAndVehicle(UUID sellerId, UUID vehicleId) {
        return list("sellerId = ?1 and vehicleId = ?2 and status in ?3", sellerId, vehicleId,
                List.of(ListingStatus.DRAFT, ListingStatus.PENDING_REVIEW, ListingStatus.PUBLISHED));
    }

    public int markVehicleVerified(UUID vehicleId, String updatedBy) {
        return update("trustLevel = ?1, updatedAt = ?2, updatedBy = ?3 where vehicleId = ?4",
                TrustLevel.REGISTRY_VERIFIED, Instant.now(), updatedBy, vehicleId);
    }

    private Sort sortOf(ListingSort sort) {
        ListingSort effective = sort == null ? ListingSort.RELEVANCE : sort;
        return switch (effective) {
            case PRICE_ASC -> Sort.ascending("price");
            case PRICE_DESC -> Sort.descending("price");
            case YEAR_DESC -> Sort.descending("modelYear");
            case RELEVANCE -> Sort.descending("trustLevel").and("publishedAt", Sort.Direction.Descending);
        };
    }
}
