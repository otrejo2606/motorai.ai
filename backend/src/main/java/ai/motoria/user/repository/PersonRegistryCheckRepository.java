package ai.motoria.user.repository;

import ai.motoria.user.domain.PersonRegistryCheck;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PersonRegistryCheckRepository implements PanacheRepositoryBase<PersonRegistryCheck, UUID> {

    /**
     * Latest check of the same identity number and provider made at or after {@code since}.
     */
    public Optional<PersonRegistryCheck> findFresh(String idNumberHash, String provider, Instant since) {
        return find("idNumberHash = ?1 and provider = ?2 and checkedAt >= ?3", Sort.descending("checkedAt"),
                idNumberHash, provider, since).firstResultOptional();
    }

    /**
     * Queries to {@code provider} made at or after {@code since} (each row is one call to the provider).
     */
    public long countCheckedSince(String provider, Instant since) {
        return count("provider = ?1 and checkedAt >= ?2", provider, since);
    }
}
