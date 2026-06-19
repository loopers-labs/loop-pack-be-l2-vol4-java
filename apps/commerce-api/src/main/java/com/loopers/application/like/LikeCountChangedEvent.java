package com.loopers.application.like;

import com.loopers.domain.like.LikeEventType;
import com.loopers.domain.like.LikeOutboxModel;

public record LikeCountChangedEvent(Long outboxId, Long productId, LikeEventType eventType) {

    public static LikeCountChangedEvent from(LikeOutboxModel outbox) {
        return new LikeCountChangedEvent(outbox.getId(), outbox.getProductId(), outbox.getEventType());
    }
}
