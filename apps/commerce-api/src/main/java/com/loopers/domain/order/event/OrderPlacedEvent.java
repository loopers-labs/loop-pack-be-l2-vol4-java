package com.loopers.domain.order.event;

public record OrderPlacedEvent(Long orderId, Long userId) {
}
