package com.loopers.domain.outbox;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "outbox_event",
    uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_id", columnNames = {"event_id"}))
public class OutboxEvent extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private String eventId;
    @Column(nullable = false)
    private String topic;
    @Column(name = "message_key", nullable = false)
    private String messageKey;
    @Column(name = "event_type", nullable = false)
    private String eventType;
    @Lob
    @Column(nullable = false)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    protected OutboxEvent() {}

    private OutboxEvent(String eventId, String topic, String messageKey, String eventType, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent pending(String eventId, String topic, String messageKey, String eventType, String payload) {
        return new OutboxEvent(eventId, topic, messageKey, eventType, payload);
    }

    public void markSent() { this.status = OutboxStatus.SENT; }

    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
}
