package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * Transactional Outbox 레코드 — "보낼 메시지"를 비즈니스 변경과 같은 트랜잭션으로 기록한다.
 *
 * <p>DB 커밋과 Kafka 발행은 하나의 트랜잭션으로 묶을 수 없다(dual write).
 * 대신 발행할 메시지를 이 테이블에 INSERT(같은 트랜잭션)하고, {@code OutboxRelayScheduler}가
 * PENDING 행을 폴링해 Kafka 로 발행한 뒤 PUBLISHED 로 마킹한다 — At Least Once 보장.
 *
 * <p>발행 완료 행은 삭제하지 않고 보관한다(발행 이력 = 감사/디버깅 자료). 정리는 별도 배치의 몫.
 *
 * <p>{@code payload} 는 Kafka 메시지 값(EventEnvelope)을 직렬화한 JSON 전문이다 —
 * 릴레이는 내용을 해석하지 않고 그대로 전송만 한다.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status_id", columnList = "status, id")
})
public class OutboxEvent extends BaseEntity {

    public enum Status { PENDING, PUBLISHED }

    /** EventEnvelope.eventId 와 동일 — 소비측 멱등 키. 릴레이 재발행 추적용으로도 쓰인다. */
    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String topic;

    /** Kafka 파티션 키 — 같은 키는 같은 파티션으로 가 순서가 보장된다 (예: productId, orderId). */
    @Column(name = "partition_key", nullable = false)
    private String partitionKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(String eventId, String topic, String partitionKey, String eventType, String payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "eventId는 필수입니다.");
        }
        if (topic == null || topic.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "topic은 필수입니다.");
        }
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "partitionKey는 필수입니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "payload는 필수입니다.");
        }
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.payload = payload;
        this.status = Status.PENDING;
    }

    /** 발행 성공 마킹 — 릴레이가 브로커 ack(acks=all)를 받은 뒤에만 호출한다. */
    public void markPublished() {
        this.status = Status.PUBLISHED;
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

    public Status getStatus() {
        return status;
    }

    public ZonedDateTime getPublishedAt() {
        return publishedAt;
    }
}
