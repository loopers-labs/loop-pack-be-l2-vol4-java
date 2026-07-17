package com.loopers.infrastructure.kafka.message;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderEventMessage(
    String eventId,
    String type,
    Long orderId,
    Long userId,
    List<Item> items,
    ZonedDateTime occurredAt
) {
    public record Item(Long productId, int quantity, Long price) {}
}
