package ai.motoria.media.repository;

import ai.motoria.media.domain.MediaAccessLog;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class MediaAccessLogRepository implements PanacheRepositoryBase<MediaAccessLog, UUID> {
}
