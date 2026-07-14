package com.loopers.infrastructure.metrics;

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

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandledJpaEntity() {
    }

    private EventHandledJpaEntity(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.handledAt = ZonedDateTime.now();
    }

    public static EventHandledJpaEntity handled(String eventId, String eventType) {
        return new EventHandledJpaEntity(eventId, eventType);
    }
}
