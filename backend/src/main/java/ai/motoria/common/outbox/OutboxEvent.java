package ai.motoria.common.outbox;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "source_module", nullable = false, length = 60)
    private String sourceModule;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "correlation_id", nullable = false, unique = true)
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    public static OutboxEvent pending(
            String eventType,
            String sourceModule,
            UUID aggregateId,
            UUID correlationId,
            String payloadJson,
            Instant occurredAt) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.eventType = eventType;
        event.sourceModule = sourceModule;
        event.aggregateId = aggregateId;
        event.correlationId = correlationId;
        event.payloadJson = payloadJson;
        event.occurredAt = occurredAt;
        event.createdAt = Instant.now();
        event.availableAt = occurredAt;
        event.status = OutboxEventStatus.PENDING;
        event.retryCount = 0;
        return event;
    }

    public void markInProgress(Instant lockedUntil) {
        this.status = OutboxEventStatus.IN_PROGRESS;
        this.lockedUntil = lockedUntil;
        this.lastError = null;
    }

    public void markSent(Instant sentAt) {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = sentAt;
        this.lockedUntil = null;
        this.lastError = null;
    }

    public void markFailed(Instant nextAttemptAt, String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.retryCount += 1;
        this.availableAt = nextAttemptAt;
        this.lockedUntil = null;
        this.lastError = truncate(errorMessage);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSourceModule() {
        return sourceModule;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }
}
