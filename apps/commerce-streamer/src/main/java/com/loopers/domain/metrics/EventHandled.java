package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.ZonedDateTime;

/**
 * 처리 완료 이벤트 기록 — 멱등 컨슈머의 핵심.
 *
 * <p>Kafka At Least Once(릴레이 재발행/컨슈머 재전달)로 같은 이벤트가 여러 번 도착할 수 있다.
 * eventId(PK) 존재 여부로 이미 처리한 이벤트를 건너뛴다.
 *
 * <p>집계 반영과 이 기록의 INSERT 는 <strong>같은 트랜잭션</strong>이다 — 동시에 같은 eventId 를
 * 처리하더라도 PK 위반으로 진 쪽 트랜잭션 전체(집계 포함)가 롤백되어 정확히 한 번만 반영된다.
 *
 * <p>{@link Persistable#isNew()} 를 항상 true 로 구현한다 — ID 를 애플리케이션이 할당하므로
 * 이게 없으면 save() 가 merge 로 동작해 건당 SELECT 가 추가되고, 동시 처리 레이스에서 기존 행을
 * 조용히 UPDATE 해 위의 PK 위반 롤백 보장이 깨진다. 이 엔티티는 INSERT 전용이라 true 고정이 안전하다.
 */
@Entity
@Table(name = "event_handled")
public class EventHandled implements Persistable<String> {

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

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return true;   // INSERT 전용 — merge(SELECT+UPDATE) 경로를 타지 않는다
    }

    public String getEventId() {
        return eventId;
    }

    public ZonedDateTime getHandledAt() {
        return handledAt;
    }
}
