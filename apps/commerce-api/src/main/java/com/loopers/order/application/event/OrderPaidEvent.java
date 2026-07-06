package com.loopers.order.application.event;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문이 결제 완료(PAID)된 사실. 판매량(PAID 기준) 재계산의 트리거로 발행한다.
 * items 는 재계산 대상 상품을 알리는 용도(값은 consumer 가 SSOT 에서 다시 계산).
 */
public record OrderPaidEvent(
        String eventId,
        Long orderId,
        List<Line> items,
        ZonedDateTime paidAt
) {
    public record Line(Long productId, int quantity) {
    }

    public static OrderPaidEvent of(Order order, List<OrderItem> orderItems) {
        return new OrderPaidEvent(
                UUID.randomUUID().toString(),
                order.getId(),
                orderItems.stream().map(item -> new Line(item.getProductId(), item.getQuantity())).toList(),
                ZonedDateTime.now()
        );
    }
}
