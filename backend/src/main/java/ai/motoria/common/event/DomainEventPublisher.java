package ai.motoria.common.event;

public interface DomainEventPublisher {
    <T> void publish(EventType eventType, String sourceModule, T payload);
}
