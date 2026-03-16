package ai.motoria.common.outbox;

public enum OutboxEventStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    SENT
}
