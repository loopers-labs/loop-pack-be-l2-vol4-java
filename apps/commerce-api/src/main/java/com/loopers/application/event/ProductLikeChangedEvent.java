package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

public record ProductLikeChangedEvent(
    String eventId,
    CatalogEventType eventType,
    Long productId,
    String userLoginId,
    int likeCountDelta,
    ZonedDateTime occurredAt
) {
    public static ProductLikeChangedEvent liked(Long productId, String userLoginId) {
        return new ProductLikeChangedEvent(
            UUID.randomUUID().toString(),
            CatalogEventType.PRODUCT_LIKED,
            productId,
            userLoginId,
            1,
            ZonedDateTime.now()
        );
    }

    public static ProductLikeChangedEvent unliked(Long productId, String userLoginId) {
        return new ProductLikeChangedEvent(
            UUID.randomUUID().toString(),
            CatalogEventType.PRODUCT_UNLIKED,
            productId,
            userLoginId,
            -1,
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
                "userLoginId", userLoginId,
                "likeCountDelta", likeCountDelta
            )
        );
    }
}
