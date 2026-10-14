package com.loopers.domain.event;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 주문이 최종 완료(결제 성공) 됐음. Consumer 는 판매량 집계에 사용.
 */
public record OrderCompletedEvent(
    Long orderId,
    Long userId,
    Long finalPrice,
    List<Line> lines,
    ZonedDateTime occurredAt
) {
    public record Line(Long productId, int quantity, long unitPrice) {}
}
