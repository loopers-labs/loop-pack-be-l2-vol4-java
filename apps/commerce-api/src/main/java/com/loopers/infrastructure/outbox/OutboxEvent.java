package com.loopers.infrastructure.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Transactional Outbox 행. 상태변경과 <b>같은 트랜잭션</b>에서 INSERT 되어, "DB 커밋 + Kafka 발행" 의
 * 원자성을 DB 로컬 트랜잭션으로 환원한다(dual-write 회피). 별도 relay 가 PENDING 을 읽어 Kafka 로 밀고 PUBLISHED 로 마킹.
 *
 * <p>도메인 애그리거트가 아니라 메시징 전파를 위한 기술적 장부이므로 {@code domain} 이 아닌 {@code infrastructure} 에 둔다.
 * {@code eventId} 는 소비자 멱등(event_handled)의 기준이 되는 전역 유일 키이며 payload 안에도 실려 나간다.</p>
 */
@Getter
@Entity
@Table(name = "outbox_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 36)
    private String eventId;

    /** Kafka 파티셔닝 키(= aggregateId). 같은 키는 항상 같은 파티션 → 키 단위 순서 보존. */
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 100)
    private String aggregateId;

    @Column(name = "topic", nullable = false, updatable = false, length = 100)
    private String topic;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    private OutboxEvent(String eventId, String aggregateId, String topic, String payload) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent pending(String eventId, String aggregateId, String topic, String payload) {
        return new OutboxEvent(eventId, aggregateId, topic, payload);
    }

    /** Kafka 발행이 broker ack 로 확인된 뒤 호출한다. */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = ZonedDateTime.now();
    }
}
