package com.loopers.domain.eventhandled;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

// 예외: BaseEntity(IDENTITY PK)를 상속하지 않는다 - PK가 IDENTITY가 아니라 eventId(String) 자체여야 하기 때문이다.
// 컨슈머 멱등 처리 전용 마커 테이블이라 BaseEntity가 관리하는 수정/삭제 이력이 필요하지 않다.
@Getter
@Entity
@Table(name = "event_handled")
public class EventHandledModel {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandledModel() {
    }

    public EventHandledModel(String eventId, ZonedDateTime handledAt) {
        this.eventId = eventId;
        this.handledAt = handledAt;
    }
}
