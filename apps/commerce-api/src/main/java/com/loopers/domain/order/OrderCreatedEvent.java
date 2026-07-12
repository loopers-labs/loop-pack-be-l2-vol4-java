package com.loopers.domain.order;

import com.loopers.domain.money.Money;

import java.util.List;

/**
 * "주문이 생성되었다"는 이미 일어난 사실을 통지하는 이벤트.
 * 주문 생성(핵심)과 무관한 후속(행동 로깅 등)은 이 이벤트를 구독해 반응한다.
 * 품목(items)은 시스템 밖(commerce-streamer)의 판매량 집계 및 랭킹 점수 계산에 쓰인다 — 수신자가 주문 DB 를 다시 조회하지 않도록 사실에 포함한다.
 */
public record OrderCreatedEvent(Long orderId, Long userId, Money paymentAmount, List<Item> items) {

    public record Item(Long productId, int quantity, Money unitPrice) {
    }

    public static OrderCreatedEvent from(Order order, List<OrderItem> orderItems) {
        List<Item> items = orderItems.stream()
            .map(orderItem -> new Item(orderItem.getProductId(), orderItem.getQuantity().getValue(), orderItem.getUnitPrice()))
            .toList();
        return new OrderCreatedEvent(order.getId(), order.getUserId(), order.getPaymentAmount(), items);
    }
}
