package com.loopers.kafka.event;

import java.time.ZonedDateTime;

public record ProductViewEventPayload(
    Long productId,
    String userId,
    ZonedDateTime occurredAt
) {
}
