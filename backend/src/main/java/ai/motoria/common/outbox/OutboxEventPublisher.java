package ai.motoria.common.outbox;

import ai.motoria.common.event.DomainEventEnvelope;
import ai.motoria.common.event.DomainEventPublisher;
import ai.motoria.common.event.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

@ApplicationScoped
public class OutboxEventPublisher implements DomainEventPublisher {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @Inject
    public OutboxEventPublisher(ObjectMapper objectMapper, OutboxEventRepository outboxEventRepository) {
        this.objectMapper = objectMapper;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public <T> void publish(EventType eventType, String sourceModule, UUID aggregateId, T payload) {
        DomainEventEnvelope<T> envelope = DomainEventEnvelope.of(eventType, sourceModule, payload);
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            outboxEventRepository.persist(OutboxEvent.pending(
                    envelope.eventType(),
                    envelope.sourceModule(),
                    aggregateId,
                    envelope.correlationId(),
                    payloadJson,
                    envelope.timestamp()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize event payload " + eventType.routingKey(), exception);
        }
    }
}
