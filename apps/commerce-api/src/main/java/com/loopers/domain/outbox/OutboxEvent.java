package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * Transactional Outbox — 도메인 불문 모든 발행 이벤트가 쌓이는 범용 전송함.
 * 비즈니스 변경과 "같은 로컬 트랜잭션" 으로 적재되어, 커밋되면 발행 대상 이벤트가 반드시 존재하도록 보장한다(유실 0).
 * 단일 Relay 가 published=false 행을 폴링해, payload 를 열어보지 않고 봉투(aggregateType→topic, aggregateId→key)만 보고
 * Kafka 로 발행한 뒤 markPublished() 로 마킹한다(At Least Once).
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    protected OutboxEvent() {}

    private OutboxEvent(String aggregateType, Long aggregateId, String eventType, String eventId, String payload) {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "aggregateType 은 비어있을 수 없습니다.");
        }
        if (aggregateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "aggregateId 는 비어있을 수 없습니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "eventType 은 비어있을 수 없습니다.");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "eventId 는 비어있을 수 없습니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "payload 는 비어있을 수 없습니다.");
        }
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventId = eventId;
        this.payload = payload;
        this.published = false;
    }

    public static OutboxEvent of(String aggregateType, Long aggregateId, String eventType, String eventId, String payload) {
        return new OutboxEvent(aggregateType, aggregateId, eventType, eventId, payload);
    }

    /**
     * Relay 가 Kafka 발행에 성공한 뒤 멱등하게 마킹한다. (이미 발행됐으면 그대로 둔다)
     */
    public void markPublished() {
        if (!this.published) {
            this.published = true;
            this.publishedAt = ZonedDateTime.now();
        }
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isPublished() {
        return published;
    }

    public ZonedDateTime getPublishedAt() {
        return publishedAt;
    }
}
