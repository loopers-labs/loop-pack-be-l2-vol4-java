package com.loopers.domain.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 멱등 처리 원장(inbox). 이미 처리한 event_id 를 기록해 중복 수신(At Least Once)을 흡수한다.
 * "처리했는가?"의 판정 전용 — 무슨 일이 있었는지의 기록(로그)과는 목적·수명주기가 달라 분리한다.
 */
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandled() {}

    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.handledAt = ZonedDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public ZonedDateTime getHandledAt() {
        return handledAt;
    }
}
