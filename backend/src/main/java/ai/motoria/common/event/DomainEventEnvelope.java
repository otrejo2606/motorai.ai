package ai.motoria.common.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEventEnvelope<T>(
        UUID correlationId,
        String eventType,
        String sourceModule,
        Instant timestamp,
        int retryCount,
        T payload) {

    public static <T> DomainEventEnvelope<T> of(EventType eventType, String sourceModule, T payload) {
        return new DomainEventEnvelope<>(
                UUID.randomUUID(),
                eventType.routingKey(),
                sourceModule,
                Instant.now(),
                0,
                payload);
    }
}
