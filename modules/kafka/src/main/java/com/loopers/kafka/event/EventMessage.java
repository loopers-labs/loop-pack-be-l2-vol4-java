package com.loopers.kafka.event;

import java.time.ZonedDateTime;

public record EventMessage(
    String eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    String payload,
    ZonedDateTime occurredAt
) {
}
