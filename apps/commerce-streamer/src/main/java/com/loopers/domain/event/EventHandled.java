package com.loopers.domain.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 멱등 판정용 처리 장부 — eventId(PK)가 이미 있으면 중복 배달된 이벤트다.
 * At Least Once 의 대가인 중복을 걸러내는 최소한의 PK 집합이므로 가볍게 유지한다(페이로드·감사 기록은 여기 두지 않는다).
 */
@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandled {

    @Id
    @Column(length = 36)
    private String eventId;

    @Column(nullable = false)
    private ZonedDateTime handledAt;

    public EventHandled(String eventId) {
        this.eventId = eventId;
        this.handledAt = ZonedDateTime.now();
    }
}
