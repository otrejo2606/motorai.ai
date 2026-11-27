package ai.motoria.user.repository;

import ai.motoria.user.domain.PersonRegistryAccess;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class PersonRegistryAccessRepository implements PanacheRepositoryBase<PersonRegistryAccess, UUID> {
}
