package ai.motoria.common.outbox;

import ai.motoria.common.event.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final OutboxEventPublisher publisher = new OutboxEventPublisher(objectMapper, outboxEventRepository);

    @Test
    void shouldPersistPendingOutboxEvent() {
        UUID aggregateId = UUID.randomUUID();

        publisher.publish(EventType.LISTING_CREATED, "listing", aggregateId, Map.of("listingId", aggregateId.toString()));

        verify(outboxEventRepository).persist(argThat((OutboxEvent event) ->
                event.getStatus() == OutboxEventStatus.PENDING
                        && event.getEventType().equals("listing.created")
                        && event.getSourceModule().equals("listing")
                        && event.getAggregateId().equals(aggregateId)
                        && event.getPayloadJson().contains(aggregateId.toString())
                        && event.getCorrelationId() != null
                        && event.getOccurredAt() != null
                        && event.getCreatedAt() != null
                        && event.getSentAt() == null
                        && event.getRetryCount() == 0));
    }
}
