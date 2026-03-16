package ai.motoria.common.event;

import java.util.UUID;

public interface DomainEventPublisher {
    <T> void publish(EventType eventType, String sourceModule, UUID aggregateId, T payload);
}
