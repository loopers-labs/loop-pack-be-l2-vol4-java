package com.loopers.application.catalog.like;

import com.loopers.domain.event.outbox.EventOutbox;

import java.time.ZonedDateTime;

public record ProductLikeChangedApplicationEvent(
    Long productId,
    String userId,
    boolean liked,
    Long likeCount,
    ZonedDateTime occurredAt
) {
    public static ProductLikeChangedApplicationEvent liked(
        Long productId,
        String userId,
        Long likeCount,
        ZonedDateTime occurredAt
    ) {
        return new ProductLikeChangedApplicationEvent(productId, userId, true, likeCount, occurredAt);
    }

    public static ProductLikeChangedApplicationEvent unliked(
        Long productId,
        String userId,
        Long likeCount,
        ZonedDateTime occurredAt
    ) {
        return new ProductLikeChangedApplicationEvent(productId, userId, false, likeCount, occurredAt);
    }

    public String eventType() {
        return liked ? EventOutbox.EVENT_PRODUCT_LIKED : EventOutbox.EVENT_PRODUCT_UNLIKED;
    }
}
