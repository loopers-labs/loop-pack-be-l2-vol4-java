package com.loopers.domain.product;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ProductViewedEvent(
        String eventId,
        Long productId,
        Long userId,
        ZonedDateTime occurredAt
) {
    public static ProductViewedEvent of(Long productId, Long userId) {
        return new ProductViewedEvent(UUID.randomUUID().toString(), productId, userId, ZonedDateTime.now());
    }
}