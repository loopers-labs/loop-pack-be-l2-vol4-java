package com.loopers.application.outbox;

import java.util.List;

public record OrderEventPayload(
    String eventId, String type, Long orderId, List<Line> lines, String occurredAt) {
    public record Line(Long productId, int quantity) {}
}
