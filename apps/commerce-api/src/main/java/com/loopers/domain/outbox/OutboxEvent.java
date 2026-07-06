package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Kafka 로 내보낼 이벤트의 발신함(Transactional Outbox).
 * 도메인 사실과 같은 트랜잭션으로 INSERT 되어 "사실은 저장됐는데 이벤트는 유실" 상태를 원천 차단한다.
 * 실제 발행은 릴레이가 트랜잭션 밖에서 수행하고, 성공 시 PUBLISHED 로 표시한다 (At Least Once).
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    // 이벤트 고유 식별자(UUID). Consumer 가 중복 판정(멱등)에 쓴다 — payload 에도 동일 값이 들어간다.
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String topic;

    // 같은 키 → 같은 파티션 → 순서 보장. catalog=productId, order=orderId.
    @Column(nullable = false, length = 50)
    private String partitionKey;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    public OutboxEvent(String eventId, String topic, String partitionKey, String eventType, String payload) {
        validate(eventId, topic, partitionKey, eventType, payload);
        this.eventId = eventId;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    private static void validate(String eventId, String topic, String partitionKey, String eventType, String payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "outbox 이벤트에는 eventId 가 필요합니다.");
        }
        if (topic == null || topic.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "outbox 이벤트에는 topic 이 필요합니다.");
        }
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "outbox 이벤트에는 partitionKey 가 필요합니다.");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "outbox 이벤트에는 eventType 이 필요합니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "outbox 이벤트에는 payload 가 필요합니다.");
        }
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
    }
}
