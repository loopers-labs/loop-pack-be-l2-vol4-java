package com.loopers.domain.order.event;

public record OrderPlacedEvent(Long orderId, Long userId, Long finalAmount) {

    public static OrderPlacedEvent of(Long orderId, Long userId, Long finalAmount) {
        return new OrderPlacedEvent(orderId, userId, finalAmount);
    }
}
