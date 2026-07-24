package com.loopers.interfaces.consumer;

import java.util.List;

/**
 * order-events 메시지(JSON). 프로듀서의 OrderEventPayload와 필드명이 일치해야 한다.
 */
public record OrderEventMessage(String eventId, Long orderId, Long userId, List<Line> lines) {

    public record Line(Long productId, int quantity) {
    }
}
