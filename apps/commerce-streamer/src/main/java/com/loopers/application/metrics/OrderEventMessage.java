package com.loopers.application.metrics;

import java.util.List;

public record OrderEventMessage(
    String eventId, String type, Long orderId, List<Line> lines, String occurredAt) {
    public record Line(Long productId, int quantity) {}
}
