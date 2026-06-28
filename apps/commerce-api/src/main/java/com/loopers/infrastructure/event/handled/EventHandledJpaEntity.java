package com.loopers.infrastructure.event.handled;

import com.loopers.domain.event.handled.EventHandled;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "event_handled")
public class EventHandledJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandledJpaEntity() {}

    private EventHandledJpaEntity(
        String eventId,
        String topic,
        String eventType,
        String aggregateType,
        String aggregateId,
        ZonedDateTime handledAt
    ) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.handledAt = handledAt;
    }

    public static EventHandledJpaEntity from(EventHandled eventHandled) {
        return new EventHandledJpaEntity(
            eventHandled.getEventId(),
            eventHandled.getTopic(),
            eventHandled.getEventType(),
            eventHandled.getAggregateType(),
            eventHandled.getAggregateId(),
            eventHandled.getHandledAt()
        );
    }

    public EventHandled toDomain() {
        return new EventHandled(eventId, topic, eventType, aggregateType, aggregateId, handledAt);
    }
}
