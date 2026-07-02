package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.UUID;

public record OrderFailedEvent(
        String eventId,
        Long orderId,
        Long userId,
        ZonedDateTime occurredAt
) {
    public static OrderFailedEvent from(OrderModel order) {
        return new OrderFailedEvent(
                UUID.randomUUID().toString(),
                order.getId(),
                order.getUserId(),
                ZonedDateTime.now()
        );
    }
}