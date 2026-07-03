package com.loopers.domain.like;

import com.loopers.domain.outbox.OutboxableEvent;

public record LikedEvent(String eventId, Long productId) implements OutboxableEvent {

    @Override
    public String aggregateType() {
        return "Like";
    }

    @Override
    public String aggregateId() {
        return String.valueOf(productId);
    }

    @Override
    public String eventType() {
        return LikeEventType.LIKED_EVENT.name();
    }
}
