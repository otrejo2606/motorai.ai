package ai.motoria.common.outbox;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class OutboxEventRepository implements PanacheRepositoryBase<OutboxEvent, UUID> {

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<OutboxEvent> claimBatch(int batchSize, Instant now, Duration claimDuration) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = entityManager.createNativeQuery("""
                select id
                from outbox_event
                where ((status = 'PENDING' or status = 'FAILED') and available_at <= :now)
                   or (status = 'IN_PROGRESS' and locked_until <= :now)
                order by occurred_at
                for update skip locked
                """)
                .setParameter("now", now)
                .setMaxResults(batchSize)
                .getResultList();

        List<UUID> ids = rawIds.stream()
                .map(this::toUuid)
                .toList();

        if (ids.isEmpty()) {
            return List.of();
        }

        Map<UUID, Integer> order = ids.stream()
                .collect(Collectors.toMap(Function.identity(), ids::indexOf));

        List<OutboxEvent> events = find("id in ?1", ids).list();
        events.sort(Comparator.comparingInt(event -> order.get(event.getId())));

        Instant lockedUntil = now.plus(claimDuration);
        for (OutboxEvent event : events) {
            event.markInProgress(lockedUntil);
        }

        return events;
    }

    @Transactional
    public void markSent(UUID eventId, Instant sentAt) {
        findByIdOptional(eventId).ifPresent(event -> event.markSent(sentAt));
    }

    @Transactional
    public void markFailed(UUID eventId, Instant nextAttemptAt, String errorMessage) {
        findByIdOptional(eventId).ifPresent(event -> event.markFailed(nextAttemptAt, errorMessage));
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }
}
