package ai.motoria.media.repository;

import ai.motoria.media.domain.PhotoProtocolSlot;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PhotoProtocolSlotRepository implements PanacheRepositoryBase<PhotoProtocolSlot, UUID> {

    public List<PhotoProtocolSlot> listByCountry(String countryCode) {
        return list("countryCode", Sort.ascending("displayOrder"), countryCode);
    }

    public Optional<PhotoProtocolSlot> findByCountryAndCode(String countryCode, String code) {
        return find("countryCode = ?1 and code = ?2", countryCode, code).firstResultOptional();
    }
}
