package com.loopers.infrastructure.outbox;

import com.loopers.application.outbox.OutboxEvent;
import com.loopers.application.outbox.OutboxEventStatus;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "outbox_event",
    indexes = {
        @Index(name = "idx_outbox_event_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_outbox_event_status_created_at", columnList = "status, created_at")
    }
)
public class OutboxEventJpaEntity extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxEventStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    protected OutboxEventJpaEntity() {
    }

    private OutboxEventJpaEntity(OutboxEvent event) {
        this.eventId = event.getEventId();
        this.topic = event.getTopic();
        this.messageKey = event.getMessageKey();
        this.eventType = event.getEventType();
        this.payload = event.getPayload();
        this.status = event.getStatus();
        this.failReason = event.getFailReason();
        this.publishedAt = event.getPublishedAt();
    }

    public static OutboxEventJpaEntity from(OutboxEvent event) {
        return new OutboxEventJpaEntity(event);
    }

    public void update(OutboxEvent event) {
        this.topic = event.getTopic();
        this.messageKey = event.getMessageKey();
        this.eventType = event.getEventType();
        this.payload = event.getPayload();
        this.status = event.getStatus();
        this.failReason = event.getFailReason();
        this.publishedAt = event.getPublishedAt();
    }

    public OutboxEvent toDomain() {
        return OutboxEvent.reconstruct(
            getId(),
            eventId,
            topic,
            messageKey,
            eventType,
            payload,
            status,
            failReason,
            publishedAt,
            getCreatedAt()
        );
    }
}
