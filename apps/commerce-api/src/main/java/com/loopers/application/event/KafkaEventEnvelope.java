package com.loopers.application.event;

import java.time.ZonedDateTime;
import java.util.Map;

public record KafkaEventEnvelope(
    String eventId,
    String eventType,
    String aggregateType,
    Long aggregateId,
    ZonedDateTime occurredAt,
    Map<String, Object> data
) {
}
