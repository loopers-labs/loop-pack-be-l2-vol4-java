package com.loopers.tddstudy.infrastructure.metrics;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    private String eventId;
    private LocalDateTime handledAt;

    protected EventHandled() {}
    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.handledAt = LocalDateTime.now();
    }
}
