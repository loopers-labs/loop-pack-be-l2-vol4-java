package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 이미 처리한 이벤트를 기록해 at-least-once 환경의 중복 수신을 걸러내는 멱등 키 테이블.
 * event_id(PK)가 이미 있으면 그 이벤트는 다시 반영하지 않는다.
 */
@Entity
@Table(name = "event_handled")
public class EventHandledModel {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandledModel() {}

    public EventHandledModel(String eventId) {
        this.eventId = eventId;
        this.handledAt = ZonedDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }
}
