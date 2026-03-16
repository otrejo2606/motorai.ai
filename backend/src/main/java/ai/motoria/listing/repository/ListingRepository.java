package ai.motoria.listing.repository;

import ai.motoria.listing.domain.Listing;
import ai.motoria.listing.domain.ListingFilter;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class ListingRepository implements PanacheRepositoryBase<Listing, UUID> {

    public List<Listing> search(ListingFilter filter) {
        StringBuilder query = new StringBuilder("1 = 1");
        Map<String, Object> parameters = new HashMap<>();

        if (filter.status() != null) {
            query.append(" and status = :status");
            parameters.put("status", filter.status());
        }
        if (filter.category() != null) {
            query.append(" and category = :category");
            parameters.put("category", filter.category());
        }
        if (filter.minPrice() != null) {
            query.append(" and price >= :minPrice");
            parameters.put("minPrice", filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            query.append(" and price <= :maxPrice");
            parameters.put("maxPrice", filter.maxPrice());
        }
        if (filter.modelYear() != null) {
            query.append(" and modelYear = :modelYear");
            parameters.put("modelYear", filter.modelYear());
        }

        return find(query.toString(), parameters)
                .page(filter.page(), filter.size())
                .list();
    }
}