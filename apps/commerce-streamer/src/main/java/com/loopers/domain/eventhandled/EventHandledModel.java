package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "event_handled")
public class EventHandledModel {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private LocalDateTime handledAt;

    protected EventHandledModel() {}

    public static EventHandledModel of(String eventId) {
        EventHandledModel model = new EventHandledModel();
        model.eventId = eventId;
        model.handledAt = LocalDateTime.now();
        return model;
    }
}
