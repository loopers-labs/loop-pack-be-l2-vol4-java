package com.loopers.infrastructure.event.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "order_event_outbox",
    indexes = {
        @Index(name = "uk_event_outbox_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_event_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_event_outbox_topic_partition", columnList = "topic, partition_key")
    }
)
public class EventOutboxJpaEntity extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    protected EventOutboxJpaEntity() {}

    private EventOutboxJpaEntity(
        String eventId,
        String topic,
        String partitionKey,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        OutboxStatus status,
        Integer retryCount
    ) {
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = status;
        this.retryCount = retryCount;
    }

    public static EventOutboxJpaEntity from(EventOutbox outbox) {
        return new EventOutboxJpaEntity(
            outbox.getEventId(),
            outbox.getTopic(),
            outbox.getPartitionKey(),
            outbox.getEventType(),
            outbox.getAggregateType(),
            outbox.getAggregateId(),
            outbox.getPayload(),
            outbox.getStatus(),
            outbox.getRetryCount()
        );
    }

    public EventOutbox toDomain() {
        return EventOutbox.reconstruct(
            getId(),
            eventId,
            topic,
            partitionKey,
            eventType,
            aggregateType,
            aggregateId,
            payload,
            status,
            retryCount,
            getCreatedAt(),
            getUpdatedAt(),
            getDeletedAt()
        );
    }

    public void apply(EventOutbox outbox) {
        this.eventId = outbox.getEventId();
        this.topic = outbox.getTopic();
        this.partitionKey = outbox.getPartitionKey();
        this.eventType = outbox.getEventType();
        this.aggregateType = outbox.getAggregateType();
        this.aggregateId = outbox.getAggregateId();
        this.payload = outbox.getPayload();
        this.status = outbox.getStatus();
        this.retryCount = outbox.getRetryCount();
    }
}
