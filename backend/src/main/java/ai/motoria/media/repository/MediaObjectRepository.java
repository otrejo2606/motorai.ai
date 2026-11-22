package ai.motoria.media.repository;

import ai.motoria.media.domain.MediaObject;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MediaObjectRepository implements PanacheRepositoryBase<MediaObject, UUID> {

    public List<MediaObject> listPageImages(UUID sourceMediaId) {
        return list("sourceMediaId", Sort.ascending("pageNumber"), sourceMediaId);
    }
}
