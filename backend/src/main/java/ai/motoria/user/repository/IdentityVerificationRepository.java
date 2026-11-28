package ai.motoria.user.repository;

import ai.motoria.user.domain.IdentityVerification;
import ai.motoria.user.domain.IdentityVerificationStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class IdentityVerificationRepository implements PanacheRepositoryBase<IdentityVerification, UUID> {

    public Optional<IdentityVerification> findLatestByUser(UUID userId) {
        return find("userId", Sort.descending("createdAt"), userId).firstResultOptional();
    }

    public List<IdentityVerification> listByUserAndStatus(UUID userId, IdentityVerificationStatus status) {
        return list("userId = ?1 and status = ?2", userId, status);
    }

    public long countSubmittedSince(Instant since) {
        return count("submittedAt >= ?1", since);
    }

    public List<IdentityVerification> pageByStatus(IdentityVerificationStatus status, int page, int size) {
        return find("status", Sort.ascending("submittedAt"), status).page(page, size).list();
    }

    public long countByStatus(IdentityVerificationStatus status) {
        return count("status", status);
    }
}
