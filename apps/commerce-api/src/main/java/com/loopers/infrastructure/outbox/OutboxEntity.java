package com.loopers.infrastructure.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * Transactional Outbox 메시지. 도메인 상태 변경과 <b>같은 트랜잭션</b>으로 INSERT되어,
 * 메시지 유실(at-most-once)을 제거하고 At Least Once 발행을 보장한다.
 *
 * <p>{@link OutboxRelay}가 {@code status=PENDING} 행을 폴링해 Kafka로 보낸 뒤 {@code SENT}로 마킹한다.
 * 전송 실패 시 PENDING으로 남아 다음 주기에 재시도된다. PK(id)가 곧 전역 고유 {@code eventId}이며
 * 소비자 멱등 키로 쓰인다.
 *
 * <p>{@code payload}는 이벤트 본문 JSON 문자열. 릴레이가 {@link EventEnvelope}로 감싸 발행한다.
 */
@Entity
@Table(
        name = "outbox",
        indexes = {
                // 릴레이 폴링: status='PENDING' 을 id ASC(= 생성 순서) 로 스캔.
                @Index(name = "idx_outbox_status_id", columnList = "status, id")
        }
)
public class OutboxEntity extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 40)
    private String aggregateType;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "partition_key", length = 100)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private OutboxStatus status;

    @Column(name = "sent_at")
    private ZonedDateTime sentAt;

    protected OutboxEntity() {}

    public OutboxEntity(String aggregateType, Long aggregateId, String eventType, String topic,
                        String partitionKey, String payload, long version) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.version = version;
        this.status = OutboxStatus.PENDING;
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = ZonedDateTime.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
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

    public String getTopic() {
        return topic;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getPayload() {
        return payload;
    }

    public long getVersion() {
        return version;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public ZonedDateTime getSentAt() {
        return sentAt;
    }
}
