package com.loopers.interfaces.consumer.message;

import java.time.ZonedDateTime;

public record LikeEventMessage(
    String eventId,
    String type,
    Long productId,
    ZonedDateTime occurredAt
) {}
