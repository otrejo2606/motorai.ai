package ai.motoria.ai.repository;

import ai.motoria.ai.domain.AiInference;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class AiInferenceRepository implements PanacheRepositoryBase<AiInference, UUID> {
}
