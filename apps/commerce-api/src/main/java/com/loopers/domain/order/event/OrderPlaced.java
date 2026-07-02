package com.loopers.domain.order.event;

import java.time.ZonedDateTime;
import java.util.List;

public record OrderPlaced(Long orderId, Long userId, long finalAmount, List<Line> lines, ZonedDateTime occurredAt) {
    public record Line(Long productId, int quantity) {}
}
