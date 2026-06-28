package com.loopers.kafka.event;

import java.time.ZonedDateTime;

public record ProductLikeEventPayload(
    Long productId,
    String userId,
    boolean liked,
    Long likeCount,
    ZonedDateTime occurredAt
) {
}
