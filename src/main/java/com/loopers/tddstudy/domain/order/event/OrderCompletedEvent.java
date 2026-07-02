package com.loopers.tddstudy.domain.order.event;

import java.util.List;

public record OrderCompletedEvent(
        Long orderId,
        Long userId,
        int totalAmount,
        List<Line> lines
) {
    public record Line(Long productId, int quantity) {}
}
