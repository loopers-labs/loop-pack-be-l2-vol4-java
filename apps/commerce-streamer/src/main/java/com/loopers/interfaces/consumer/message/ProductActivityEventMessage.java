package com.loopers.interfaces.consumer.message;

import java.time.Instant;
import java.util.UUID;

public record ProductActivityEventMessage(
    UUID eventId,
    int version,
    ProductActivityEventType eventType,
    Instant occurredAt,
    Long productId,
    Long unitPrice,
    Integer quantity) {}
