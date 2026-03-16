package ai.motoria.common.event;

import ai.motoria.common.outbox.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class RabbitMqEventPublisher {

    private final ObjectMapper objectMapper;
    private final MutinyEmitter<String> emitter;

    @Inject
    public RabbitMqEventPublisher(ObjectMapper objectMapper, @Channel("motoria-events-out") MutinyEmitter<String> emitter) {
        this.objectMapper = objectMapper;
        this.emitter = emitter;
    }

    public void publish(OutboxEvent outboxEvent) {
        try {
            JsonNode payload = objectMapper.readTree(outboxEvent.getPayloadJson());
            String json = objectMapper.writeValueAsString(DomainEventEnvelope.restore(
                    outboxEvent.getCorrelationId(),
                    outboxEvent.getEventType(),
                    outboxEvent.getSourceModule(),
                    outboxEvent.getOccurredAt(),
                    outboxEvent.getRetryCount(),
                    payload));

            Message<String> message = Message.of(json)
                    .addMetadata(OutgoingRabbitMQMetadata.builder()
                            .withRoutingKey(outboxEvent.getEventType())
                            .build());

            emitter.sendMessageAndAwait(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize outbox event " + outboxEvent.getEventType(), exception);
        }
    }
}
