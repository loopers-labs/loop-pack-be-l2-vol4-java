package com.loopers.domain.event.handled;

import java.time.ZonedDateTime;

public class EventHandled {

    private final String eventId;
    private final String topic;
    private final String eventType;
    private final String aggregateType;
    private final String aggregateId;
    private final ZonedDateTime handledAt;

    public EventHandled(
        String eventId,
        String topic,
        String eventType,
        String aggregateType,
        String aggregateId,
        ZonedDateTime handledAt
    ) {
        validateRequired(eventId, "이벤트 ID는 필수입니다.");
        validateRequired(topic, "topic은 필수입니다.");
        validateRequired(eventType, "이벤트 타입은 필수입니다.");
        validateRequired(aggregateType, "aggregate type은 필수입니다.");
        validateRequired(aggregateId, "aggregate ID는 필수입니다.");
        if (handledAt == null) {
            throw new IllegalArgumentException("처리 시각은 필수입니다.");
        }

        this.eventId = eventId;
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.handledAt = handledAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public ZonedDateTime getHandledAt() {
        return handledAt;
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
