package com.loopers.application.outbox;

import java.time.ZonedDateTime;

public class OutboxEvent {

    private final Long id;
    private final String eventId;
    private final String topic;
    private final String messageKey;
    private final String eventType;
    private final String payload;
    private OutboxEventStatus status;
    private String failReason;
    private ZonedDateTime publishedAt;
    private final ZonedDateTime createdAt;

    public OutboxEvent(String eventId, String topic, String messageKey, String eventType, String payload) {
        this(null, eventId, topic, messageKey, eventType, payload, OutboxEventStatus.READY, null, null, null);
    }

    private OutboxEvent(
        Long id,
        String eventId,
        String topic,
        String messageKey,
        String eventType,
        String payload,
        OutboxEventStatus status,
        String failReason,
        ZonedDateTime publishedAt,
        ZonedDateTime createdAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.status = status;
        this.failReason = failReason;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
    }

    public static OutboxEvent reconstruct(
        Long id,
        String eventId,
        String topic,
        String messageKey,
        String eventType,
        String payload,
        OutboxEventStatus status,
        String failReason,
        ZonedDateTime publishedAt,
        ZonedDateTime createdAt
    ) {
        return new OutboxEvent(id, eventId, topic, messageKey, eventType, payload, status, failReason, publishedAt, createdAt);
    }

    public void markPublished(ZonedDateTime publishedAt) {
        this.status = OutboxEventStatus.PUBLISHED;
        this.failReason = null;
        this.publishedAt = publishedAt;
    }

    public void markFailed(String failReason) {
        this.status = OutboxEventStatus.FAILED;
        this.failReason = failReason;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public String getFailReason() {
        return failReason;
    }

    public ZonedDateTime getPublishedAt() {
        return publishedAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }
}
