package com.loopers.outbox.domain;

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
 * Transactional Outbox. 도메인 변경과 "같은 트랜잭션"으로 적재되어, 별도 릴레이가 Kafka 로 발행한다.
 * payload 는 직렬화된 이벤트(JSON 문자열)이며, 릴레이는 이를 그대로 발행한다(이벤트 타입을 몰라도 됨).
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    private OutboxEvent(String topic, String messageKey, String eventType, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent pending(String topic, String messageKey, String eventType, String payload) {
        return new OutboxEvent(topic, messageKey, eventType, payload);
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = ZonedDateTime.now();
    }
}
