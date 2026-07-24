package com.loopers.application.outbox;

import java.util.List;

/**
 * order-events 토픽으로 나가는 메시지 payload(JSON). eventId는 컨슈머 멱등 처리용,
 * lines는 상품별 판매량 집계 재료.
 */
public record OrderEventPayload(String eventId, Long orderId, Long userId, List<Line> lines) {

    public record Line(Long productId, int quantity) {
    }
}
