package ai.motoria.common.outbox;

import ai.motoria.common.event.RabbitMqEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatcherTest {

    private final OutboxEventRepository outboxEventRepository = mock(OutboxEventRepository.class);
    private final RabbitMqEventPublisher rabbitMqEventPublisher = mock(RabbitMqEventPublisher.class);
    private final OutboxDispatcher dispatcher = new OutboxDispatcher(
            outboxEventRepository,
            rabbitMqEventPublisher,
            true,
            20,
            Duration.ofSeconds(30),
            Duration.ofSeconds(15));

    @Test
    void shouldMarkOutboxEventAsSentAfterSuccessfulPublish() {
        OutboxEvent event = OutboxEvent.pending(
                "listing.created",
                "listing",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"listingId\":\"123\"}",
                Instant.now());

        dispatcher.dispatchSingleEvent(event);

        verify(rabbitMqEventPublisher).publish(event);
        verify(outboxEventRepository).markSent(eq(event.getId()), any(Instant.class));
    }

    @Test
    void shouldMarkOutboxEventForRetryWhenPublishFails() {
        OutboxEvent event = OutboxEvent.pending(
                "listing.created",
                "listing",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"listingId\":\"123\"}",
                Instant.now());
        doThrow(new IllegalStateException("broker unavailable")).when(rabbitMqEventPublisher).publish(event);

        dispatcher.dispatchSingleEvent(event);

        verify(outboxEventRepository).markFailed(eq(event.getId()), any(Instant.class), eq("broker unavailable"));
    }

    @Test
    void shouldClaimAndDispatchPendingEventsWhenEnabled() {
        OutboxEvent event = OutboxEvent.pending(
                "listing.created",
                "listing",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "{\"listingId\":\"123\"}",
                Instant.now());
        when(outboxEventRepository.claimBatch(eq(20), any(Instant.class), eq(Duration.ofSeconds(30))))
                .thenReturn(List.of(event));

        dispatcher.dispatchPendingEvents();

        verify(outboxEventRepository).claimBatch(eq(20), any(Instant.class), eq(Duration.ofSeconds(30)));
        verify(rabbitMqEventPublisher).publish(event);
    }
}
