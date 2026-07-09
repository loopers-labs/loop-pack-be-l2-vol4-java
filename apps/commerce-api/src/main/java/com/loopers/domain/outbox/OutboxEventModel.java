package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * Transactional Outbox — 도메인 트랜잭션과 같은 트랜잭션에서 저장되어,
 * 실제 Kafka 발행(별도 스케줄러)과 도메인 상태 변경의 원자성을 보장한다.
 */
@Getter
@Entity
@Table(name = "outbox_events")
public class OutboxEventModel extends BaseEntity {

    // Consumer의 event_handled 멱등 처리에서 참조하는 논리적 이벤트 ID (outbox의 PK와는 별개 — 발행 전에 payload에 먼저 포함되어야 하므로 애플리케이션에서 생성)
    @Column(name = "event_id", nullable = false, updatable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "topic", nullable = false, updatable = false, length = 100)
    private String topic;

    @Column(name = "message_key", nullable = false, updatable = false, length = 100)
    private String messageKey;

    @Lob
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    protected OutboxEventModel() {}

    public OutboxEventModel(String eventId, String topic, String messageKey, String payload) {
        if (eventId == null || eventId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이벤트 ID는 필수입니다.");
        }
        if (topic == null || topic.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "토픽은 필수입니다.");
        }
        if (messageKey == null || messageKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "메시지 키는 필수입니다.");
        }
        if (payload == null || payload.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "페이로드는 필수입니다.");
        }
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = ZonedDateTime.now();
    }
}
