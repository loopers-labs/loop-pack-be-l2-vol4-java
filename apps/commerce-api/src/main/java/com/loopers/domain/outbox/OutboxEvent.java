package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * Transactional Outbox 레코드. 비즈니스 트랜잭션과 같은 트랜잭션에서 저장되어, 별도 발행기(relay)가
 * 이를 읽어 Kafka로 전송한다. "DB 커밋"과 "Kafka 발행"의 이중 쓰기 문제를 끊어 at-least-once를 보장한다.
 */
@Entity
@Table(
    name = "outbox_event",
    indexes = @Index(name = "idx_outbox_status_id", columnList = "status, id") // 폴러가 WHERE status='PENDING' ORDER BY id로 조회할 수 있도록 인덱스 추가
) 
public class OutboxEvent extends BaseEntity {

    /** 컨슈머 멱등 처리(중복 제거)의 키. 메시지 payload에도 실려 나간다. */
    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 36)
    private String eventId;

    @Column(name = "topic", nullable = false, updatable = false)
    private String topic;

    /** Kafka 파티션 키 (예: productId, orderId). */
    @Column(name = "partition_key", nullable = false, updatable = false)
    private String partitionKey;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String eventId, String topic, String partitionKey, String eventType, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    /** 발행 성공 시 호출. 폴러가 이후 재조회 대상에서 제외되도록 상태를 전이한다. */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = ZonedDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }
}
