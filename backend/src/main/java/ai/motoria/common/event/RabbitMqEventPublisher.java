package ai.motoria.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class RabbitMqEventPublisher implements DomainEventPublisher {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("motoria-events-out")
    Emitter<String> emitter;

    @Override
    public <T> void publish(EventType eventType, String sourceModule, T payload) {
        try {
            String json = objectMapper.writeValueAsString(DomainEventEnvelope.of(eventType, sourceModule, payload));
            Message<String> message = Message.of(json)
                    .addMetadata(OutgoingRabbitMQMetadata.builder()
                            .withRoutingKey(eventType.routingKey())
                            .build());
            emitter.send(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize event " + eventType.routingKey(), exception);
        }
    }
}