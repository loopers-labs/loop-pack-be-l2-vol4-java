package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

public record ProductViewedEvent(
    String eventId,
    CatalogEventType eventType,
    Long productId,
    String userLoginId,
    int viewCountDelta,
    ZonedDateTime occurredAt
) {
    public static ProductViewedEvent viewed(Long productId, String userLoginId) {
        return new ProductViewedEvent(
            UUID.randomUUID().toString(),
            CatalogEventType.PRODUCT_VIEWED,
            productId,
            userLoginId,
            1,
            ZonedDateTime.now()
        );
    }

    public KafkaEventEnvelope toEnvelope() {
        return new KafkaEventEnvelope(
            eventId,
            eventType.name(),
            "PRODUCT",
            productId,
            occurredAt,
            Map.of(
                "productId", productId,
                "userLoginId", userLoginId == null ? "" : userLoginId,
                "viewCountDelta", viewCountDelta
            )
        );
    }
}
