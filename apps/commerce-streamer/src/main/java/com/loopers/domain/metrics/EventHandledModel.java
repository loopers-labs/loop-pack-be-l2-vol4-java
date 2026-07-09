package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 처리 완료한 이벤트 기록(멱등 판단용). event_id 를 PK 로 두어 이미 반영한 이벤트의 재소비를 걸러낸다.
 * "무엇을 처리했는가"만 담는 작은 테이블이라 원본 로그 테이블과 분리한다.
 */
@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandledModel {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    private EventHandledModel(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.handledAt = ZonedDateTime.now();
    }

    public static EventHandledModel of(String eventId, String eventType) {
        return new EventHandledModel(eventId, eventType);
    }
}