package com.loopers.domain.eventhandled;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 이벤트 소비 완료 기록 — Consumer 멱등 처리의 핵심 테이블.
 * <p>
 * <b>event_log(전문 감사용 로그) 과 분리된 이유</b>:
 * <ul>
 *   <li>접근 패턴 다름: 여기는 hot lookup (매 이벤트마다 UNIQUE 조회), event_log 는 append-only</li>
 *   <li>보존 정책 다름: 여기는 짧게 두어도 무방(기간 후 purge), event_log 는 감사용 장기 보존</li>
 *   <li>스키마 진화 속도 다름: 여기는 최소 필드만 안정적, event_log 는 payload 스키마 변경 잦음</li>
 * </ul>
 * UNIQUE(event_id, consumer_group) — 같은 이벤트를 그룹별로 독립 처리하도록 함.
 */
@Entity
@Table(
    name = "event_handled",
    indexes = @Index(
        name = "uk_event_handled_event_group",
        columnList = "event_id, consumer_group",
        unique = true
    )
)
public class EventHandledRecord extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 40, updatable = false)
    private String eventId;

    @Column(name = "consumer_group", nullable = false, length = 60, updatable = false)
    private String consumerGroup;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "handled_at", nullable = false)
    private ZonedDateTime handledAt;

    protected EventHandledRecord() {}

    public EventHandledRecord(String eventId, String consumerGroup, String eventType, ZonedDateTime handledAt) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId 는 필수입니다.");
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            throw new IllegalArgumentException("consumerGroup 은 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType 은 필수입니다.");
        }
        this.eventId = eventId;
        this.consumerGroup = consumerGroup;
        this.eventType = eventType;
        this.handledAt = handledAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public String getEventType() {
        return eventType;
    }

    public ZonedDateTime getHandledAt() {
        return handledAt;
    }
}
