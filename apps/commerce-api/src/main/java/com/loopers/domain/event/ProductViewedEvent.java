package com.loopers.domain.event;

import java.time.ZonedDateTime;

public record ProductViewedEvent(
    Long userId,
    Long productId,
    ZonedDateTime occurredAt
) {
    public static ProductViewedEvent of(Long userId, Long productId) {
        return new ProductViewedEvent(userId, productId, ZonedDateTime.now());
    }
}
