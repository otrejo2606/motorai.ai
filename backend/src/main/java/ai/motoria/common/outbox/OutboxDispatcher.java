package ai.motoria.common.outbox;

import ai.motoria.common.event.RabbitMqEventPublisher;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class OutboxDispatcher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitMqEventPublisher rabbitMqEventPublisher;
    private final boolean enabled;
    private final int batchSize;
    private final Duration claimDuration;
    private final Duration retryDelay;

    @Inject
    public OutboxDispatcher(
            OutboxEventRepository outboxEventRepository,
            RabbitMqEventPublisher rabbitMqEventPublisher,
            @ConfigProperty(name = "motoria.outbox.dispatch.enabled", defaultValue = "true") boolean enabled,
            @ConfigProperty(name = "motoria.outbox.dispatch.batch-size", defaultValue = "20") int batchSize,
            @ConfigProperty(name = "motoria.outbox.dispatch.claim-duration", defaultValue = "PT30S") Duration claimDuration,
            @ConfigProperty(name = "motoria.outbox.dispatch.retry-delay", defaultValue = "PT30S") Duration retryDelay) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitMqEventPublisher = rabbitMqEventPublisher;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.claimDuration = claimDuration;
        this.retryDelay = retryDelay;
    }

    @Scheduled(every = "{motoria.outbox.dispatch.every:5s}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void dispatchPendingEvents() {
        if (!enabled) {
            return;
        }

        Instant now = Instant.now();
        List<OutboxEvent> events = outboxEventRepository.claimBatch(batchSize, now, claimDuration);
        for (OutboxEvent event : events) {
            dispatchSingleEvent(event);
        }
    }

    void dispatchSingleEvent(OutboxEvent event) {
        try {
            rabbitMqEventPublisher.publish(event);
            outboxEventRepository.markSent(event.getId(), Instant.now());
        } catch (Exception exception) {
            outboxEventRepository.markFailed(event.getId(), Instant.now().plus(retryDelay), exception.getMessage());
        }
    }
}
