package com.loopers.domain.eventpublish;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Transactional Outbox — 도메인 트랜잭션과 함께 커밋되어 이벤트 발행의 원자성을 보장.
 * <p>
 * eventId 는 이벤트 자체의 고유 식별자. Consumer 는 이 값으로 멱등 처리를 판단한다.
 * partitionKey 는 Kafka partition 결정에 사용 — 같은 aggregate 이벤트의 순서를 보장한다.
 */
@Entity
@Table(name = "outbox_message", indexes = {
    @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
    @Index(name = "idx_outbox_event_id", columnList = "event_id", unique = true)
})
public class OutboxMessage extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 40, updatable = false)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 40, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 80, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 60, updatable = false)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 60, updatable = false)
    private String topic;

    @Column(name = "partition_key", nullable = false, length = 80, updatable = false)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "sent_at")
    private ZonedDateTime sentAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxMessage() {}

    private OutboxMessage(
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String partitionKey,
        String payload
    ) {
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "aggregateType 은 필수입니다.");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "aggregateId 는 필수입니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "eventType 은 필수입니다.");
        }
        if (topic == null || topic.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "topic 은 필수입니다.");
        }
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "partitionKey 는 필수입니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "payload 는 필수입니다.");
        }
        this.eventId = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public static OutboxMessage create(
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String partitionKey,
        String payload
    ) {
        return new OutboxMessage(aggregateType, aggregateId, eventType, topic, partitionKey, payload);
    }

    public String getEventId() {
        return eventId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public ZonedDateTime getSentAt() {
        return sentAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    /**
     * 성공 전송으로 마킹. 이미 SENT 면 멱등 통과.
     */
    public void markSent(ZonedDateTime at) {
        if (this.status == OutboxStatus.SENT) {
            return;
        }
        this.status = OutboxStatus.SENT;
        this.sentAt = at;
        this.lastError = null;
    }

    /**
     * 실패로 마킹 — retryCount 증가. 재폴링 대상으로 남긴다.
     */
    public void markFailed(String errorMessage) {
        this.status = OutboxStatus.PENDING;
        this.retryCount += 1;
        this.lastError = truncate(errorMessage, 500);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
