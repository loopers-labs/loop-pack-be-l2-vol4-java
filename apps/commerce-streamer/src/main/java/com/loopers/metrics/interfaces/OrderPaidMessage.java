package com.loopers.metrics.interfaces;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * order-events 토픽에서 소비하는 결제완료 메시지. producer(commerce-api)의 OrderPaidEvent JSON 과
 * 필드명이 일치해야 한다. items 의 productId 를 판매량 재계산 트리거로 쓴다.
 */
public record OrderPaidMessage(
        String eventId,
        Long orderId,
        List<Line> items,
        ZonedDateTime paidAt
) {
    public record Line(Long productId, int quantity) {
    }
}
