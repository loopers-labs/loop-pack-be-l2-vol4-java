package com.loopers.domain.like;

import com.loopers.domain.UserActivityEvent;
import com.loopers.domain.outbox.OutboxableEvent;

public record UnlikedEvent(String eventId, Long userId, Long productId) implements OutboxableEvent, UserActivityEvent {

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
        return LikeEventType.UNLIKED_EVENT.name();
    }
}
