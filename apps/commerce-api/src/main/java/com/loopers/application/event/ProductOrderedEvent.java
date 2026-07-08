package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

public record ProductOrderedEvent(
    String eventId,
    CatalogEventType eventType,
    Long productId,
    Long orderId,
    String userLoginId,
    int salesCountDelta,
    ZonedDateTime occurredAt
) {
    public static ProductOrderedEvent ordered(Long productId, Long orderId, String userLoginId, int quantity) {
        return new ProductOrderedEvent(
            UUID.randomUUID().toString(),
            CatalogEventType.PRODUCT_ORDERED,
            productId,
            orderId,
            userLoginId,
            quantity,
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
                "orderId", orderId,
                "userLoginId", userLoginId,
                "salesCountDelta", salesCountDelta
            )
        );
    }
}
