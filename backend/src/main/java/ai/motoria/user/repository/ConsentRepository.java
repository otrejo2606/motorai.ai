package ai.motoria.user.repository;

import ai.motoria.user.domain.Consent;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ConsentRepository implements PanacheRepositoryBase<Consent, UUID> {

    public List<Consent> listByUser(UUID userId) {
        return list("userId", Sort.ascending("decidedAt"), userId);
    }
}
