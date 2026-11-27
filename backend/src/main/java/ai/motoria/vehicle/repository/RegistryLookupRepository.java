package ai.motoria.vehicle.repository;

import ai.motoria.vehicle.domain.RegistryLookup;
import ai.motoria.vehicle.domain.RegistryLookupOutcome;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class RegistryLookupRepository implements PanacheRepositoryBase<RegistryLookup, UUID> {

    /**
     * Answered calls to {@code provider} made at or after {@code since} (FOUND or NOT_FOUND: the ones the provider
     * bills). Failed calls do not consume the daily quota.
     */
    public long countAnsweredSince(String provider, Instant since) {
        return count("provider = ?1 and checkedAt >= ?2 and outcome in ?3", provider, since,
                List.of(RegistryLookupOutcome.FOUND, RegistryLookupOutcome.NOT_FOUND));
    }
}
