package com.loopers.order.application.event;

import com.loopers.order.domain.Order;
import com.loopers.order.domain.OrderItem;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문이 생성(PENDING)된 "사실"을 알리는 도메인 이벤트.
 *
 * - eventId : 이벤트 인스턴스마다 고유한 멱등 key(UUID). consumer 의 event_handled 중복 차단용.
 *             orderId 와 별개다 — orderId 는 "파티션 key(순서)", eventId 는 "중복 차단 key".
 * - orderId : Kafka 발행 시 파티션 key 로 사용해 주문별 순서를 보장한다.
 * - items   : 집계(판매량) consumer 가 상품별로 합산할 수 있도록 주문 라인을 담는다.
 */
public record OrderCreatedEvent(
        String eventId,
        Long orderId,
        Long userId,
        String orderNumber,
        long finalAmount,
        List<Line> items,
        ZonedDateTime orderedAt
) {
    public record Line(Long productId, int quantity) {
    }

    public static OrderCreatedEvent of(Order order, List<OrderItem> orderItems) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                order.getId(),
                order.getUserId(),
                order.getOrderNumber(),
                order.getFinalAmount().value(),
                orderItems.stream().map(item -> new Line(item.getProductId(), item.getQuantity())).toList(),
                order.getOrderedAt()
        );
    }
}
