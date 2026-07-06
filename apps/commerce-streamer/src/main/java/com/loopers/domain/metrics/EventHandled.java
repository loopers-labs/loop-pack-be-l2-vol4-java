package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 처리 완료 이벤트 기록 — 멱등 컨슈머의 핵심.
 *
 * <p>Kafka At Least Once(릴레이 재발행/컨슈머 재전달)로 같은 이벤트가 여러 번 도착할 수 있다.
 * eventId(PK) 존재 여부로 이미 처리한 이벤트를 건너뛴다.
 *
 * <p>집계 반영과 이 기록의 INSERT 는 <strong>같은 트랜잭션</strong>이다 — 동시에 같은 eventId 를
 * 처리하더라도 PK 위반으로 진 쪽 트랜잭션 전체(집계 포함)가 롤백되어 정확히 한 번만 반영된다.
 */
@Entity
@Table(name = "event_handled")
public class EventHandled {

    @Id
    @Column(name = "event_id", length = 36)
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
